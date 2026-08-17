package dev.codeatlas.agent;

import dev.codeatlas.config.CodeAtlasProperties;
import dev.codeatlas.project.ProjectService;
import dev.codeatlas.project.ProjectStatus;
import dev.codeatlas.project.SourceFileRecord;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AgentService {
    private static final Pattern WORD_SPLIT = Pattern.compile("[^\\p{L}\\p{N}_./-]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "请问", "一下", "如何", "什么", "这个", "项目", "代码", "哪些", "怎么", "实现"
    );

    private final ConversationRepository conversations;
    private final ProjectService projects;
    private final CodeAtlasProperties properties;
    private final ChatClient chatClient;

    public AgentService(ConversationRepository conversations,
                        ProjectService projects,
                        CodeAtlasProperties properties,
                        ChatClient.Builder chatClientBuilder) {
        this.conversations = conversations;
        this.projects = projects;
        this.properties = properties;
        this.chatClient = chatClientBuilder.build();
    }

    public AnalysisSession createSession(UUID projectId, String title) {
        projects.get(projectId);
        String normalized = title == null || title.isBlank() ? "新的代码分析" : title.trim();
        return conversations.createSession(projectId, normalized.substring(0, Math.min(200, normalized.length())));
    }

    public List<AnalysisSession> sessions(UUID projectId) {
        projects.get(projectId);
        return conversations.listSessions(projectId);
    }

    public List<ChatMessage> messages(UUID sessionId) {
        requireSession(sessionId);
        return conversations.messages(sessionId);
    }

    public ChatMessage ask(UUID sessionId, String question) {
        if (question == null || question.isBlank()) throw new IllegalArgumentException("问题不能为空");
        AnalysisSession session = requireSession(sessionId);
        var project = projects.get(session.projectId());
        if (project.status() != ProjectStatus.READY) {
            throw new IllegalArgumentException("仓库尚未完成索引");
        }
        conversations.addMessage(sessionId, "user", question.trim(), List.of());

        if (properties.aiApiKey() == null || properties.aiApiKey().isBlank()) {
            return localAnalysis(session, question.trim());
        }

        RepositoryTools tools = new RepositoryTools(session.projectId(), projects);
        String recentHistory = conversations.messages(sessionId).stream()
                .skip(Math.max(0, conversations.messages(sessionId).size() - 8L))
                .map(message -> message.role() + ": " + message.content())
                .reduce((a, b) -> a + "\n" + b).orElse("");
        try {
            String answer = chatClient.prompt()
                    .system("""
                            你是 CodeAtlas，一个严谨的代码库理解与变更影响分析 Agent。
                            所有结论必须来自工具返回的当前仓库证据。先获取项目概览，再搜索符号和调用，必要时读取文件。
                            不要声称执行过代码。回答使用中文，并包含：结论、证据、调用关系或影响范围、建议下一步。
                            引用文件时写出完整仓库相对路径。信息不足时明确说明并继续使用工具查找。
                            """)
                    .user("对话上下文:\n" + recentHistory + "\n\n当前问题:\n" + question)
                    .tools(tools)
                    .call()
                    .content();
            if (answer == null || answer.isBlank()) throw new IllegalStateException("模型没有返回内容");
            return conversations.addMessage(sessionId, "assistant", answer, tools.citations());
        } catch (Exception exception) {
            return localAnalysis(session, question + "\n\n模型调用失败，已切换本地分析模式。");
        }
    }

    private ChatMessage localAnalysis(AnalysisSession session, String question) {
        LinkedHashSet<SourceFileRecord> matches = new LinkedHashSet<>();
        for (String token : WORD_SPLIT.split(question)) {
            String normalized = token.trim();
            if (normalized.length() < 2 || STOP_WORDS.contains(normalized)) continue;
            matches.addAll(projects.search(session.projectId(), normalized).stream().limit(4).toList());
            if (matches.size() >= 8) break;
        }
        if (matches.isEmpty()) {
            matches.addAll(projects.files(session.projectId()).stream()
                    .filter(file -> file.path().endsWith("README.md") || file.path().endsWith("pom.xml")
                            || file.path().endsWith("package.json"))
                    .limit(6).toList());
        }

        List<String> citations = matches.stream().map(SourceFileRecord::path).distinct().toList();
        StringBuilder answer = new StringBuilder();
        answer.append("## 本地分析结果\n\n")
                .append("当前没有配置大模型 API，因此我使用代码索引完成关键词与结构检索。\n\n")
                .append("### 相关文件\n\n");
        for (SourceFileRecord file : matches) {
            answer.append("- `").append(file.path()).append("`（")
                    .append(file.language()).append("，").append(file.lineCount()).append(" 行）\n");
        }
        answer.append("\n### 下一步\n\n")
                .append("打开上述文件核对调用关系；配置 `AI_API_KEY` 后，Agent 会自动调用搜索和文件读取工具，生成带证据的完整分析。");
        return conversations.addMessage(session.id(), "assistant", answer.toString(), citations);
    }

    private AnalysisSession requireSession(UUID sessionId) {
        return conversations.findSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("分析会话不存在"));
    }
}

