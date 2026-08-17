package dev.codeatlas.project;

import java.util.UUID;

public record SourceFileRecord(
        UUID id,
        UUID projectId,
        String path,
        String language,
        String content,
        long byteSize,
        int lineCount,
        String sha256
) {
}
