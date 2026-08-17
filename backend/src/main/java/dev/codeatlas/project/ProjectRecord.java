package dev.codeatlas.project;

import java.time.Instant;
import java.util.UUID;

public record ProjectRecord(
        UUID id,
        String name,
        String repositoryUrl,
        String branch,
        ProjectStatus status,
        String statusMessage,
        int fileCount,
        Instant indexedAt,
        Instant createdAt,
        Instant updatedAt
) {
}

