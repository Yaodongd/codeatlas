package dev.codeatlas.project;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProjectRepository {
    private final JdbcClient jdbc;

    public ProjectRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public ProjectRecord create(String name, String repositoryUrl, String branch) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO projects(id, name, repository_url, branch, status)
                VALUES (:id, :name, :repositoryUrl, :branch, :status)
                """)
                .param("id", id)
                .param("name", name)
                .param("repositoryUrl", repositoryUrl)
                .param("branch", branch)
                .param("status", ProjectStatus.PENDING.name())
                .update();
        return findById(id).orElseThrow();
    }

    public List<ProjectRecord> findAll() {
        return jdbc.sql("SELECT * FROM projects ORDER BY created_at DESC")
                .query(ProjectRepository::mapProject)
                .list();
    }

    public Optional<ProjectRecord> findById(UUID id) {
        return jdbc.sql("SELECT * FROM projects WHERE id = :id")
                .param("id", id)
                .query(ProjectRepository::mapProject)
                .optional();
    }

    public void updateStatus(UUID id, ProjectStatus status, String message) {
        jdbc.sql("""
                UPDATE projects
                SET status = :status, status_message = :message, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """)
                .param("id", id)
                .param("status", status.name())
                .param("message", message)
                .update();
    }

    public void markReady(UUID id, int fileCount) {
        jdbc.sql("""
                UPDATE projects
                SET status = 'READY', status_message = '索引完成', file_count = :fileCount,
                    indexed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id
                """)
                .param("id", id)
                .param("fileCount", fileCount)
                .update();
    }

    public void delete(UUID id) {
        jdbc.sql("DELETE FROM projects WHERE id = :id").param("id", id).update();
    }

    private static ProjectRecord mapProject(ResultSet rs, int rowNum) throws SQLException {
        var indexedAt = rs.getTimestamp("indexed_at");
        return new ProjectRecord(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("repository_url"),
                rs.getString("branch"),
                ProjectStatus.valueOf(rs.getString("status")),
                rs.getString("status_message"),
                rs.getInt("file_count"),
                indexedAt == null ? null : indexedAt.toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}

