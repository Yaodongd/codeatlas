package dev.codeatlas.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService service;
    private final ProjectAnalysisService analysis;

    public ProjectController(ProjectService service, ProjectAnalysisService analysis) {
        this.service = service;
        this.analysis = analysis;
    }

    @GetMapping
    public List<ProjectRecord> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ProjectRecord get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ProjectRecord create(@Valid @RequestBody CreateProjectRequest request) {
        return service.create(request.name(), request.repositoryUrl(), request.branch());
    }

    @GetMapping("/{id}/files")
    public List<SourceFileRecord> files(@PathVariable UUID id) {
        return service.files(id);
    }

    @GetMapping("/{id}/file")
    public SourceFileRecord file(@PathVariable UUID id, @RequestParam String path) {
        return service.file(id, path);
    }

    @GetMapping("/{id}/search")
    public List<SourceFileRecord> search(@PathVariable UUID id, @RequestParam String query) {
        return service.search(id, query);
    }

    @GetMapping("/{id}/insights")
    public ProjectAnalysisService.ProjectInsights insights(@PathVariable UUID id) {
        service.get(id);
        return analysis.insights(id);
    }

    @GetMapping("/{id}/graph")
    public ProjectAnalysisService.ProjectGraph graph(@PathVariable UUID id) {
        service.get(id);
        return analysis.graph(id);
    }

    @GetMapping("/{id}/impact")
    public ProjectAnalysisService.ImpactAnalysis impact(@PathVariable UUID id, @RequestParam String path) {
        service.get(id);
        return analysis.impact(id, path);
    }

    @PostMapping("/{id}/reindex")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ProjectRecord reindex(@PathVariable UUID id) {
        return service.reindex(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    public record CreateProjectRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 500) String repositoryUrl,
            @Size(max = 120) String branch
    ) {
    }
}
