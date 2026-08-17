package dev.codeatlas.agent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatMessage(
        UUID id,
        UUID sessionId,
        String role,
        String content,
        List<String> citations,
        Instant createdAt
) {
}

