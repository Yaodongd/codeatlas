package dev.codeatlas.project;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SourceFileRepository {
    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;

    public SourceFileRepository(JdbcClient jdbc, JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void replaceAll(UUID projectId, List<SourceFileRecord> files) {
        jdbc.sql("DELETE FROM source_files WHERE project_id = :projectId")
                .param("projectId", projectId)
                .update();

        jdbcTemplate.batchUpdate("""
                        INSERT INTO source_files
                        (id, project_id, path, language, content, byte_size, line_count, sha256)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                files,
                100,
                (statement, file) -> {
                    statement.setObject(1, file.id());
                    statement.setObject(2, file.projectId());
                    statement.setString(3, file.path());
                    statement.setString(4, file.language());
                    statement.setString(5, file.content());
                    statement.setLong(6, file.byteSize());
                    statement.setInt(7, file.lineCount());
                    statement.setString(8, file.sha256());
                });
    }

    public List<SourceFileRecord> list(UUID projectId) {
        return jdbc.sql("""
                SELECT id, project_id, path, language, '' AS content, byte_size, line_count, sha256
                FROM source_files WHERE project_id = :projectId ORDER BY path
                """)
                .param("projectId", projectId)
                .query(SourceFileRepository::mapFile)
                .list();
    }

    public List<SourceFileRecord> listWithContent(UUID projectId) {
        return jdbc.sql("SELECT * FROM source_files WHERE project_id = :projectId ORDER BY path")
                .param("projectId", projectId)
                .query(SourceFileRepository::mapFile)
                .list();
    }

    public Optional<SourceFileRecord> findByPath(UUID projectId, String path) {
        return jdbc.sql("SELECT * FROM source_files WHERE project_id = :projectId AND path = :path")
                .param("projectId", projectId)
                .param("path", path)
                .query(SourceFileRepository::mapFile)
                .optional();
    }

    public List<SourceFileRecord> search(UUID projectId, String query, int limit) {
        String pattern = "%" + query.replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.sql("""
                SELECT * FROM source_files
                WHERE project_id = :projectId
                  AND (path ILIKE :pattern ESCAPE '\\' OR content ILIKE :pattern ESCAPE '\\')
                ORDER BY CASE WHEN path ILIKE :pattern ESCAPE '\\' THEN 0 ELSE 1 END, path
                LIMIT :limit
                """)
                .param("projectId", projectId)
                .param("pattern", pattern)
                .param("limit", limit)
                .query(SourceFileRepository::mapFile)
                .list();
    }

    private static SourceFileRecord mapFile(ResultSet rs, int rowNum) throws SQLException {
        return new SourceFileRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("path"),
                rs.getString("language"),
                rs.getString("content"),
                rs.getLong("byte_size"),
                rs.getInt("line_count"),
                rs.getString("sha256")
        );
    }
}
