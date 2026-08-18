package dev.codeatlas.project;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class RepositoryIndexRecovery {
    private static final Set<ProjectStatus> INTERRUPTED = Set.of(
            ProjectStatus.PENDING, ProjectStatus.CLONING, ProjectStatus.INDEXING
    );

    private final ProjectRepository projects;
    private final RepositoryIndexer indexer;

    public RepositoryIndexRecovery(ProjectRepository projects, RepositoryIndexer indexer) {
        this.projects = projects;
        this.indexer = indexer;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeInterruptedIndexes() {
        projects.findAll().stream()
                .filter(project -> INTERRUPTED.contains(project.status()))
                .forEach(indexer::index);
    }
}
