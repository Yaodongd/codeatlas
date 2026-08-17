package dev.codeatlas.project;

import java.util.UUID;

public class ProjectNotFoundException extends RuntimeException {
    public ProjectNotFoundException(UUID id) {
        super("项目不存在: " + id);
    }
}

