package dev.codeatlas.project;

import dev.codeatlas.config.CodeAtlasProperties;
import org.eclipse.jgit.api.Git;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipInputStream;

@Service
public class RepositoryIndexer {
    private static final Set<String> INCLUDED_EXTENSIONS = Set.of(
            "java", "kt", "kts", "xml", "gradle", "properties", "yml", "yaml",
            "js", "jsx", "ts", "tsx", "vue", "css", "scss", "html", "json",
            "sql", "md", "dockerfile", "sh", "ps1", "py", "go", "rs"
    );
    private static final Set<String> EXCLUDED_NAMES = Set.of(
            ".env", "id_rsa", "id_ed25519", "credentials", "secrets.yml", "secrets.yaml"
    );

    private final ProjectRepository projects;
    private final IndexPersistenceService persistence;
    private final CodeAtlasProperties properties;
    private final StringRedisTemplate redis;

    public RepositoryIndexer(ProjectRepository projects,
                             IndexPersistenceService persistence,
                             CodeAtlasProperties properties,
                             StringRedisTemplate redis) {
        this.projects = projects;
        this.persistence = persistence;
        this.properties = properties;
        this.redis = redis;
    }

    @Async
    public void index(ProjectRecord project) {
        Path root = properties.repositoryStorage().toAbsolutePath().normalize();
        Path target = root.resolve(project.id().toString()).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalStateException("仓库存储路径越界");
        }

        try {
            projects.updateStatus(project.id(), ProjectStatus.CLONING, "正在连接代码托管平台");
            publishProgress(project.id(), "CONNECTING", 8);
            Files.createDirectories(root);
            deleteSafely(root, target);

            fetchRepository(project, root, target);

            projects.updateStatus(project.id(), ProjectStatus.INDEXING, "正在扫描源文件");
            publishProgress(project.id(), "SCANNING", 58);
            var indexed = scan(project.id(), target);
            projects.updateStatus(project.id(), ProjectStatus.INDEXING, "正在分析代码关系并保存索引");
            publishProgress(project.id(), "ANALYZING", 82);
            persistence.replace(project.id(), indexed);
            projects.markReady(project.id(), indexed.size());
            publishProgress(project.id(), "READY", 100);
        } catch (Exception exception) {
            projects.updateStatus(project.id(), ProjectStatus.FAILED, safeMessage(exception));
            publishProgress(project.id(), "FAILED", 100);
        }
    }

    public ProjectProgress progress(ProjectRecord project) {
        String stage = project.status().name();
        int percent = switch (project.status()) {
            case PENDING -> 2;
            case CLONING -> 12;
            case INDEXING -> 65;
            case READY, FAILED -> 100;
        };
        try {
            String cached = redis.opsForValue().get("codeatlas:index:" + project.id());
            if (cached != null) {
                int separator = cached.lastIndexOf(':');
                if (separator > 0) {
                    stage = cached.substring(0, separator);
                    percent = Integer.parseInt(cached.substring(separator + 1));
                }
            }
        } catch (Exception ignored) {
            // The project row still provides a useful fallback if Redis is unavailable.
        }
        return new ProjectProgress(stage, Math.max(0, Math.min(100, percent)), project.statusMessage());
    }

    public void remove(ProjectRecord project) {
        Path root = properties.repositoryStorage().toAbsolutePath().normalize();
        Path target = root.resolve(project.id().toString()).normalize();
        try {
            deleteSafely(root, target);
        } catch (IOException exception) {
            throw new IllegalStateException("无法清理仓库文件", exception);
        }
    }

    private ArrayList<SourceFileRecord> scan(UUID projectId, Path target) throws Exception {
        var indexed = new ArrayList<SourceFileRecord>();
        try (var paths = Files.walk(target)) {
            var iterator = paths.filter(Files::isRegularFile).iterator();
            while (iterator.hasNext()) {
                Path file = iterator.next();
                if (indexed.size() >= properties.maxRepositoryFiles()) {
                    break;
                }
                Path relative = target.relativize(file);
                if (shouldSkip(relative, file)) {
                    continue;
                }
                byte[] bytes = Files.readAllBytes(file);
                if (containsNullByte(bytes)) {
                    continue;
                }
                String content = new String(bytes, StandardCharsets.UTF_8);
                String path = relative.toString().replace('\\', '/');
                indexed.add(new SourceFileRecord(
                        UUID.randomUUID(), projectId, path, language(path), content,
                        bytes.length, content.isEmpty() ? 0 : content.split("\\R", -1).length,
                        sha256(bytes)
                ));
            }
        }
        return indexed;
    }

    private void fetchRepository(ProjectRecord project, Path root, Path target) throws Exception {
        if (isGitHubRepository(project.repositoryUrl())) {
            projects.updateStatus(project.id(), ProjectStatus.CLONING, "正在通过 GitHub 官方源码归档获取代码");
            publishProgress(project.id(), "DOWNLOADING", 24);
            downloadGitHubArchive(project, target);
            publishProgress(project.id(), "EXTRACTING", 48);
            return;
        }

        var clone = Git.cloneRepository()
                .setURI(project.repositoryUrl())
                .setDirectory(target.toFile())
                .setDepth(1)
                .setCloneSubmodules(false)
                .setTimeout(properties.repositoryConnectTimeoutSeconds());
        if (!project.branch().isBlank()) clone.setBranch(project.branch());
        try (Git ignored = clone.call()) {
            // Closing the repository releases file handles before indexing.
        }
        publishProgress(project.id(), "DOWNLOADING", 38);
    }

    private void downloadGitHubArchive(ProjectRecord project, Path target) throws Exception {
        URI archiveUri = githubArchiveUri(project.repositoryUrl(), project.branch());
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.repositoryConnectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(archiveUri)
                .timeout(Duration.ofMinutes(3))
                .header("Accept", "application/zip")
                .header("User-Agent", "CodeAtlas/0.1")
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("GitHub 官方归档返回 HTTP " + response.statusCode());
        }
        extractArchive(response.body(), target, properties.maxRepositoryArchiveBytes());
    }

    private static void extractArchive(InputStream input, Path target, long maxBytes) throws IOException {
        Files.createDirectories(target);
        long extractedBytes = 0;
        byte[] buffer = new byte[16 * 1024];
        try (input; var zip = new ZipInputStream(input)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                String name = entry.getName().replace('\\', '/');
                int rootSeparator = name.indexOf('/');
                if (rootSeparator < 0 || rootSeparator == name.length() - 1) continue;
                String relativeName = name.substring(rootSeparator + 1);
                Path output = target.resolve(relativeName).normalize();
                if (!output.startsWith(target)) throw new IOException("源码归档包含非法路径");
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                    continue;
                }
                Files.createDirectories(output.getParent());
                try (var destination = Files.newOutputStream(output)) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        extractedBytes += read;
                        if (extractedBytes > maxBytes) throw new IOException("源码归档超过允许大小");
                        destination.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    static URI githubArchiveUri(String repositoryUrl, String branch) {
        URI repository = URI.create(repositoryUrl);
        String[] segments = repository.getPath().replaceFirst("^/", "").split("/");
        if (segments.length < 2) throw new IllegalArgumentException("GitHub 仓库地址缺少 owner/repository");
        String owner = segments[0];
        String name = segments[1].replaceFirst("\\.git$", "");
        String reference = branch == null || branch.isBlank() ? "HEAD" : "refs/heads/" + branch;
        return URI.create("https://codeload.github.com/" + owner + "/" + name + "/zip/" + reference);
    }

    private static boolean isGitHubRepository(String repositoryUrl) {
        return "github.com".equalsIgnoreCase(URI.create(repositoryUrl).getHost());
    }

    private boolean shouldSkip(Path relative, Path file) throws IOException {
        String normalized = relative.toString().replace('\\', '/');
        if (normalized.startsWith(".git/") || normalized.contains("/node_modules/")
                || normalized.contains("/target/") || normalized.contains("/dist/")) {
            return true;
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (EXCLUDED_NAMES.contains(name) || name.endsWith(".pem") || name.endsWith(".key")) {
            return true;
        }
        return Files.size(file) > properties.maxFileBytes() || !isIncluded(name);
    }

    private static boolean isIncluded(String name) {
        if (name.equals("dockerfile") || name.equals("makefile")) return true;
        int dot = name.lastIndexOf('.');
        return dot >= 0 && INCLUDED_EXTENSIONS.contains(name.substring(dot + 1));
    }

    private static boolean containsNullByte(byte[] bytes) {
        for (byte value : bytes) if (value == 0) return true;
        return false;
    }

    private static String language(String path) {
        String name = path.toLowerCase(Locale.ROOT);
        if (name.endsWith(".java")) return "java";
        if (name.endsWith(".vue")) return "vue";
        if (name.endsWith(".ts") || name.endsWith(".tsx")) return "typescript";
        if (name.endsWith(".js") || name.endsWith(".jsx")) return "javascript";
        if (name.endsWith(".sql")) return "sql";
        if (name.endsWith(".yml") || name.endsWith(".yaml")) return "yaml";
        if (name.endsWith(".md")) return "markdown";
        return "text";
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private void publishProgress(UUID projectId, String status, int percent) {
        try {
            redis.opsForValue().set("codeatlas:index:" + projectId, status + ":" + percent, Duration.ofHours(1));
        } catch (Exception ignored) {
            // Database status remains authoritative if Redis is temporarily unavailable.
        }
    }

    private static void deleteSafely(Path root, Path target) throws IOException {
        if (!target.startsWith(root) || target.equals(root) || !Files.exists(target)) return;
        try (var paths = Files.walk(target)) {
            for (Path path : paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static String safeMessage(Exception exception) {
        String value = exception.getMessage();
        if (value != null && value.toLowerCase(Locale.ROOT).contains("time")) {
            return "连接代码托管平台超时。已尝试 Git 克隆和 GitHub 官方归档，请检查服务器出站网络后重试。";
        }
        return value == null || value.isBlank() ? exception.getClass().getSimpleName()
                : value.substring(0, Math.min(value.length(), 500));
    }

    public record ProjectProgress(String stage, int percent, String message) {}
}
