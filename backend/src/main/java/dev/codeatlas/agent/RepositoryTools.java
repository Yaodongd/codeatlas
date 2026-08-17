package dev.codeatlas.agent;

import dev.codeatlas.project.ProjectRecord;
import dev.codeatlas.project.ProjectService;
import dev.codeatlas.project.SourceFileRecord;
import org.springframework.ai.tool.annotation.Tool;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class RepositoryTools {
    private final UUID projectId;
    private final ProjectService projects;
    private final Set<String> citations = new LinkedHashSet<>();

    public RepositoryTools(UUID projectId, ProjectService projects) {
        this.projectId = projectId;
        this.projects = projects;
    }

    @Tool(description = "查看当前代码库的基本信息、语言分布和主要目录。分析项目架构时先调用。")
    public String projectOverview() {
        ProjectRecord project = projects.get(projectId);
        List<SourceFileRecord> files = projects.files(projectId);
        var languageCounts = files.stream()
                .collect(java.util.stream.Collectors.groupingBy(SourceFileRecord::language,
                        java.util.stream.Collectors.counting()));
        String sample = files.stream().limit(80).map(SourceFileRecord::path)
                .reduce((a, b) -> a + "\n" + b).orElse("没有文件");
        return "项目: " + project.name() + "\n仓库: " + project.repositoryUrl()
                + "\n文件数: " + files.size() + "\n语言: " + languageCounts
                + "\n主要文件:\n" + sample;
    }

    @Tool(description = "在当前代码库中搜索关键词、类名、方法名、接口路径或配置项。返回匹配文件和附近代码。")
    public String searchCode(String query) {
        List<SourceFileRecord> matches = projects.search(projectId, query).stream().limit(8).toList();
        if (matches.isEmpty()) return "没有找到与 " + query + " 相关的代码";
        StringBuilder output = new StringBuilder();
        for (SourceFileRecord file : matches) {
            citations.add(file.path());
            output.append("\n--- ").append(file.path()).append(" ---\n")
                    .append(snippet(file.content(), query, 14));
        }
        return output.toString();
    }

    @Tool(description = "读取当前代码库中的一个文件。行号从 1 开始，一次最多读取 240 行。")
    public String readFile(String path, int startLine, int endLine) {
        SourceFileRecord file = projects.file(projectId, path);
        citations.add(file.path());
        String[] lines = file.content().split("\\R", -1);
        int start = Math.max(1, startLine);
        int end = Math.min(lines.length, Math.max(start, Math.min(endLine, start + 239)));
        StringBuilder output = new StringBuilder("文件: ").append(file.path()).append('\n');
        for (int i = start; i <= end; i++) {
            output.append(i).append(" | ").append(lines[i - 1]).append('\n');
        }
        return output.toString();
    }

    @Tool(description = "按路径关键词筛选文件，例如 controller、Dockerfile、migration 或 LoginView。")
    public String listFiles(String pathContains) {
        String needle = pathContains == null ? "" : pathContains.toLowerCase();
        return projects.files(projectId).stream()
                .map(SourceFileRecord::path)
                .filter(path -> path.toLowerCase().contains(needle))
                .limit(120)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("没有匹配文件");
    }

    @Tool(description = "查找 Java、TypeScript 或 Vue 中的类、接口、函数、变量及其引用。")
    public String findSymbol(String symbol) {
        return searchCode(symbol);
    }

    public List<String> citations() {
        return List.copyOf(citations);
    }

    private static String snippet(String content, String query, int radius) {
        String[] lines = content.split("\\R", -1);
        int hit = 0;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].toLowerCase().contains(query.toLowerCase())) {
                hit = i;
                break;
            }
        }
        int start = Math.max(0, hit - radius);
        int end = Math.min(lines.length, hit + radius + 1);
        StringBuilder output = new StringBuilder();
        for (int i = start; i < end; i++) {
            output.append(i + 1).append(" | ").append(lines[i]).append('\n');
        }
        return output.toString();
    }
}

