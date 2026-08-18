package dev.codeatlas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "codeatlas")
public record CodeAtlasProperties(
        Path repositoryStorage,
        int maxRepositoryFiles,
        long maxFileBytes,
        int repositoryConnectTimeoutSeconds,
        long maxRepositoryArchiveBytes,
        String aiApiKey
) {
}
