package dev.codeatlas.agent;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ConversationRepository {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public ConversationRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public AnalysisSession createSession(UUID projectId, String title) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO analysis_sessions(id, project_id, title) VALUES (:id, :projectId, :title)")
                .param("id", id)
                .param("projectId", projectId)
                .param("title", title)
                .update();
        return findSession(id).orElseThrow();
    }

    public List<AnalysisSession> listSessions(UUID projectId) {
        return jdbc.sql("SELECT * FROM analysis_sessions WHERE project_id = :projectId ORDER BY updated_at DESC")
                .param("projectId", projectId)
                .query(ConversationRepository::mapSession)
                .list();
    }

    public Optional<AnalysisSession> findSession(UUID id) {
        return jdbc.sql("SELECT * FROM analysis_sessions WHERE id = :id")
                .param("id", id)
                .query(ConversationRepository::mapSession)
                .optional();
    }

    public ChatMessage addMessage(UUID sessionId, String role, String content, List<String> citations) {
        UUID id = UUID.randomUUID();
        String citationsJson;
        try {
            citationsJson = objectMapper.writeValueAsString(citations);
        } catch (Exception exception) {
            throw new IllegalStateException("无法保存引用", exception);
        }
        jdbc.sql("""
                INSERT INTO chat_messages(id, session_id, role, content, citations)
                VALUES (:id, :sessionId, :role, :content, :citations)
                """)
                .param("id", id)
                .param("sessionId", sessionId)
                .param("role", role)
                .param("content", content)
                .param("citations", citationsJson)
                .update();
        jdbc.sql("UPDATE analysis_sessions SET updated_at = CURRENT_TIMESTAMP WHERE id = :id")
                .param("id", sessionId)
                .update();
        return messages(sessionId).stream().filter(message -> message.id().equals(id)).findFirst().orElseThrow();
    }

    public List<ChatMessage> messages(UUID sessionId) {
        return jdbc.sql("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY created_at, id")
                .param("sessionId", sessionId)
                .query(this::mapMessage)
                .list();
    }

    private ChatMessage mapMessage(ResultSet rs, int rowNum) throws SQLException {
        try {
            return new ChatMessage(
                    rs.getObject("id", UUID.class),
                    rs.getObject("session_id", UUID.class),
                    rs.getString("role"),
                    rs.getString("content"),
                    objectMapper.readValue(rs.getString("citations"), STRING_LIST),
                    rs.getTimestamp("created_at").toInstant()
            );
        } catch (SQLException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SQLException("无法读取消息引用", exception);
        }
    }

    private static AnalysisSession mapSession(ResultSet rs, int rowNum) throws SQLException {
        return new AnalysisSession(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("title"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
