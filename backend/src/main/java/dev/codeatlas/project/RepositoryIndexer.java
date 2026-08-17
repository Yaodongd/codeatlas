package dev.codeatlas.project;

import dev.codeatlas.config.CodeAtlasProperties;
import org.eclipse.jgit.api.Git;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
            projects.updateStatus(project.id(), ProjectStatus.CLONING, "正在克隆公共仓库");
            publishProgress(project.id(), "CLONING", 5);
            Files.createDirectories(root);
            deleteSafely(root, target);

            var clone = Git.cloneRepository()
                    .setURI(project.repositoryUrl())
                    .setDirectory(target.toFile())
                    .setDepth(1)
                    .setCloneSubmodules(false)
                    .setTimeout(120);
            if (!project.branch().isBlank()) {
                clone.setBranch(project.branch());
            }
            try (Git ignored = clone.call()) {
                // Closing the repository releases file handles before indexing.
            }

            projects.updateStatus(project.id(), ProjectStatus.INDEXING, "正在建立代码索引");
            publishProgress(project.id(), "INDEXING", 25);
            var indexed = scan(project.id(), target);
            persistence.replace(project.id(), indexed);
            projects.markReady(project.id(), indexed.size());
            publishProgress(project.id(), "READY", 100);
        } catch (Exception exception) {
            projects.updateStatus(project.id(), ProjectStatus.FAILED, safeMessage(exception));
            publishProgress(project.id(), "FAILED", 100);
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
        return value == null || value.isBlank() ? exception.getClass().getSimpleName()
                : value.substring(0, Math.min(value.length(), 500));
    }
}
