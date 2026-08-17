package dev.codeatlas.agent;

import java.time.Instant;
import java.util.UUID;

public record AnalysisSession(UUID id, UUID projectId, String title, Instant createdAt, Instant updatedAt) {
}

