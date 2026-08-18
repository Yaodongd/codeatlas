package dev.codeatlas.project;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {
    private final ProjectRepository projects;
    private final SourceFileRepository sourceFiles;
    private final RepositoryUrlValidator urlValidator;
    private final RepositoryIndexer indexer;

    public ProjectService(ProjectRepository projects,
                          SourceFileRepository sourceFiles,
                          RepositoryUrlValidator urlValidator,
                          RepositoryIndexer indexer) {
        this.projects = projects;
        this.sourceFiles = sourceFiles;
        this.urlValidator = urlValidator;
        this.indexer = indexer;
    }

    public ProjectRecord create(String name, String repositoryUrl, String branch) {
        var uri = urlValidator.validate(repositoryUrl);
        String normalizedBranch = branch == null ? "" : branch.trim();
        if (!normalizedBranch.isEmpty()
                && (!normalizedBranch.matches("[A-Za-z0-9._/-]{1,120}") || normalizedBranch.contains(".."))) {
            throw new IllegalArgumentException("分支名称不合法");
        }
        ProjectRecord project = projects.create(name.trim(), uri.toString(), normalizedBranch);
        indexer.index(project);
        return project;
    }

    public List<ProjectRecord> list() {
        return projects.findAll();
    }

    public RepositoryIndexer.ProjectProgress progress(UUID projectId) {
        return indexer.progress(get(projectId));
    }

    public ProjectRecord get(UUID id) {
        return projects.findById(id).orElseThrow(() -> new ProjectNotFoundException(id));
    }

    public List<SourceFileRecord> files(UUID projectId) {
        get(projectId);
        return sourceFiles.list(projectId);
    }

    public SourceFileRecord file(UUID projectId, String path) {
        get(projectId);
        return sourceFiles.findByPath(projectId, path)
                .orElseThrow(() -> new IllegalArgumentException("文件不存在"));
    }

    public List<SourceFileRecord> search(UUID projectId, String query) {
        get(projectId);
        if (query == null || query.isBlank()) return List.of();
        return sourceFiles.search(projectId, query.trim(), 20);
    }

    public ProjectRecord reindex(UUID projectId) {
        ProjectRecord project = get(projectId);
        if (indexer.isRunning(projectId)) {
            throw new IllegalStateException("项目正在建立索引，请稍后再试");
        }
        projects.updateStatus(projectId, ProjectStatus.PENDING, "等待重新索引");
        ProjectRecord pending = get(projectId);
        indexer.index(pending);
        return pending;
    }

    public void delete(UUID projectId) {
        ProjectRecord project = get(projectId);
        if (indexer.isRunning(projectId)) {
            throw new IllegalStateException("项目正在建立索引，暂时不能删除");
        }
        indexer.remove(project);
        projects.delete(projectId);
    }
}
