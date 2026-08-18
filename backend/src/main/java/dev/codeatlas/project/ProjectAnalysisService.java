package dev.codeatlas.project;

import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ProjectAnalysisService {
    private static final Pattern JAVA_IMPORT = Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?([\\w.]+)");
    private static final Pattern MODULE_IMPORT = Pattern.compile("(?:from\\s*|require\\s*\\(|import\\s*\\()?['\"]([^'\"]+)['\"]");
    private static final int MAX_EDGES = 5000;

    private final SourceFileRepository files;
    private final ProjectRepository projects;

    public ProjectAnalysisService(SourceFileRepository files, ProjectRepository projects) {
        this.files = files;
        this.projects = projects;
    }

    public ProjectInsights insights(UUID projectId) {
        List<SourceFileRecord> all = files.list(projectId);
        Map<String, Integer> languages = new LinkedHashMap<>();
        Map<String, Integer> directories = new HashMap<>();
        long totalBytes = 0;
        long totalLines = 0;
        for (SourceFileRecord file : all) {
            languages.merge(file.language(), 1, Integer::sum);
            directories.merge(topDirectory(file.path()), 1, Integer::sum);
            totalBytes += file.byteSize();
            totalLines += file.lineCount();
        }
        List<Metric> languageCounts = languages.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(entry -> new Metric(entry.getKey(), entry.getValue()))
                .toList();
        List<Metric> topDirectories = directories.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(8)
                .map(entry -> new Metric(entry.getKey(), entry.getValue()))
                .toList();
        List<FileSummary> largestFiles = all.stream()
                .sorted(Comparator.comparingLong(SourceFileRecord::byteSize).reversed())
                .limit(8)
                .map(ProjectAnalysisService::summary)
                .toList();
        List<FileSummary> entryPoints = all.stream()
                .filter(file -> isEntryPoint(file.path()))
                .limit(12)
                .map(ProjectAnalysisService::summary)
                .toList();
        Instant indexedAt = projects.findById(projectId).map(ProjectRecord::indexedAt).orElse(null);
        return new ProjectInsights(all.size(), totalLines, totalBytes, languageCounts, topDirectories,
                largestFiles, entryPoints, indexedAt);
    }

    public ProjectGraph graph(UUID projectId) {
        List<SourceFileRecord> all = files.listWithContent(projectId);
        Map<String, SourceFileRecord> byPath = new HashMap<>();
        Map<String, List<SourceFileRecord>> byStem = new HashMap<>();
        for (SourceFileRecord file : all) {
            byPath.put(normalize(file.path()), file);
            byStem.computeIfAbsent(stem(file.path()), ignored -> new ArrayList<>()).add(file);
        }

        List<GraphNode> nodes = all.stream().map(file -> new GraphNode(
                file.path(), file.path(), file.language(), folder(file.path()), file.lineCount(), file.byteSize()
        )).toList();
        Set<String> seen = new LinkedHashSet<>();
        List<GraphEdge> edges = new ArrayList<>();

        for (SourceFileRecord source : all) {
            addJavaEdges(source, byStem, edges, seen);
            addModuleEdges(source, byPath, edges, seen);
            if (edges.size() >= MAX_EDGES) break;
        }
        if (edges.size() < Math.min(20, all.size())) {
            addStructuralEdges(all, edges, seen);
        }
        return new ProjectGraph(nodes, edges.stream().limit(MAX_EDGES).toList());
    }

    public ImpactAnalysis impact(UUID projectId, String path) {
        String normalizedPath = normalize(path);
        ProjectGraph graph = graph(projectId);
        if (graph.nodes().stream().noneMatch(node -> node.path().equals(normalizedPath))) {
            throw new IllegalArgumentException("文件不存在");
        }
        Map<String, List<String>> outgoing = new HashMap<>();
        Map<String, List<String>> incoming = new HashMap<>();
        for (GraphEdge edge : graph.edges()) {
            outgoing.computeIfAbsent(edge.source(), ignored -> new ArrayList<>()).add(edge.target());
            incoming.computeIfAbsent(edge.target(), ignored -> new ArrayList<>()).add(edge.source());
        }
        List<ImpactNode> dependencies = traverse(normalizedPath, outgoing, 3);
        List<ImpactNode> dependents = traverse(normalizedPath, incoming, 3);
        int score = Math.min(100, dependents.size() * 12 + dependencies.size() * 5);
        String risk = score >= 60 ? "HIGH" : score >= 25 ? "MEDIUM" : "LOW";
        return new ImpactAnalysis(normalizedPath, risk, score, dependencies, dependents);
    }

    private static void addJavaEdges(SourceFileRecord source,
                                     Map<String, List<SourceFileRecord>> byStem,
                                     List<GraphEdge> edges,
                                     Set<String> seen) {
        var matcher = JAVA_IMPORT.matcher(source.content());
        while (matcher.find() && edges.size() < MAX_EDGES) {
            String imported = matcher.group(1);
            String simple = imported.substring(imported.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            List<SourceFileRecord> targets = byStem.getOrDefault(simple, List.of());
            if (targets.size() == 1) addEdge(source.path(), targets.getFirst().path(), "import", edges, seen);
        }
    }

    private static void addModuleEdges(SourceFileRecord source,
                                       Map<String, SourceFileRecord> byPath,
                                       List<GraphEdge> edges,
                                       Set<String> seen) {
        var matcher = MODULE_IMPORT.matcher(source.content());
        while (matcher.find() && edges.size() < MAX_EDGES) {
            String module = matcher.group(1);
            if (!module.startsWith(".")) continue;
            String parent = folder(source.path());
            String base = normalize(Path.of(parent).resolve(module).normalize().toString());
            for (String candidate : List.of(base, base + ".ts", base + ".tsx", base + ".js", base + ".jsx",
                    base + ".vue", base + "/index.ts", base + "/index.js")) {
                SourceFileRecord target = byPath.get(candidate);
                if (target != null) {
                    addEdge(source.path(), target.path(), "import", edges, seen);
                    break;
                }
            }
        }
    }

    private static void addStructuralEdges(List<SourceFileRecord> all, List<GraphEdge> edges, Set<String> seen) {
        Map<String, List<SourceFileRecord>> folders = new LinkedHashMap<>();
        for (SourceFileRecord file : all) folders.computeIfAbsent(folder(file.path()), ignored -> new ArrayList<>()).add(file);
        for (List<SourceFileRecord> group : folders.values()) {
            for (int index = 1; index < group.size() && edges.size() < MAX_EDGES; index++) {
                addEdge(group.get(index - 1).path(), group.get(index).path(), "package", edges, seen);
            }
        }
    }

    private static void addEdge(String source, String target, String type, List<GraphEdge> edges, Set<String> seen) {
        if (source.equals(target)) return;
        String key = source + "\u0000" + target;
        if (seen.add(key)) edges.add(new GraphEdge(source, target, type, "import".equals(type) ? 3 : 1));
    }

    private static List<ImpactNode> traverse(String root, Map<String, List<String>> adjacency, int maxDepth) {
        List<ImpactNode> result = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        visited.add(root);
        ArrayDeque<ImpactNode> queue = new ArrayDeque<>();
        queue.add(new ImpactNode(root, 0));
        while (!queue.isEmpty()) {
            ImpactNode current = queue.removeFirst();
            if (current.depth() >= maxDepth) continue;
            for (String next : adjacency.getOrDefault(current.path(), List.of())) {
                if (visited.add(next)) {
                    ImpactNode discovered = new ImpactNode(next, current.depth() + 1);
                    result.add(discovered);
                    queue.addLast(discovered);
                }
            }
        }
        return result.stream().limit(80).toList();
    }

    private static boolean isEntryPoint(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith("application.java") || lower.endsWith("main.java") || lower.endsWith("main.ts")
                || lower.endsWith("main.js") || lower.endsWith("app.vue") || lower.endsWith("index.ts")
                || lower.endsWith("index.js") || lower.endsWith("dockerfile") || lower.endsWith("compose.yaml")
                || lower.endsWith("compose.yml");
    }

    private static FileSummary summary(SourceFileRecord file) {
        return new FileSummary(file.path(), file.language(), file.lineCount(), file.byteSize());
    }

    private static String normalize(String path) {
        return path.replace('\\', '/').replaceFirst("^\\./", "");
    }

    private static String folder(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "." : path.substring(0, slash);
    }

    private static String topDirectory(String path) {
        int slash = path.indexOf('/');
        return slash < 0 ? "root" : path.substring(0, slash);
    }

    private static String stem(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return (dot < 0 ? name : name.substring(0, dot)).toLowerCase(Locale.ROOT);
    }

    public record Metric(String name, int value) {}
    public record FileSummary(String path, String language, int lineCount, long byteSize) {}
    public record ProjectInsights(int totalFiles, long totalLines, long totalBytes,
                                  List<Metric> languages, List<Metric> topDirectories,
                                  List<FileSummary> largestFiles, List<FileSummary> entryPoints,
                                  Instant indexedAt) {}
    public record GraphNode(String id, String path, String language, String folder, int lineCount, long byteSize) {}
    public record GraphEdge(String source, String target, String type, int weight) {}
    public record ProjectGraph(List<GraphNode> nodes, List<GraphEdge> edges) {}
    public record ImpactNode(String path, int depth) {}
    public record ImpactAnalysis(String sourcePath, String risk, int score,
                                 List<ImpactNode> dependencies, List<ImpactNode> dependents) {}
}
