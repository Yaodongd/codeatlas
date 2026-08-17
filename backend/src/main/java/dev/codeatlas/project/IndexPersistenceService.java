package dev.codeatlas.project;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class IndexPersistenceService {
    private final SourceFileRepository sourceFiles;

    public IndexPersistenceService(SourceFileRepository sourceFiles) {
        this.sourceFiles = sourceFiles;
    }

    @Transactional
    public void replace(UUID projectId, List<SourceFileRecord> files) {
        sourceFiles.replaceAll(projectId, files);
    }
}

