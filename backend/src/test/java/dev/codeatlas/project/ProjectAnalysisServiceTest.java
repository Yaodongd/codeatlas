package dev.codeatlas.project;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectAnalysisServiceTest {
    private final SourceFileRepository files = mock(SourceFileRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ProjectAnalysisService service = new ProjectAnalysisService(files, projects);

    @Test
    void buildsJavaImportEdgesFromIndexedContent() {
        UUID projectId = UUID.randomUUID();
        SourceFileRecord controller = file(projectId, "src/main/java/demo/UserController.java", "java",
                "package demo;\nimport demo.UserService;\nclass UserController {}\n");
        SourceFileRecord serviceFile = file(projectId, "src/main/java/demo/UserService.java", "java",
                "package demo;\nclass UserService {}\n");
        when(files.listWithContent(projectId)).thenReturn(List.of(controller, serviceFile));

        ProjectAnalysisService.ProjectGraph graph = service.graph(projectId);

        assertThat(graph.nodes()).hasSize(2);
        assertThat(graph.edges()).anySatisfy(edge -> {
            assertThat(edge.source()).isEqualTo(controller.path());
            assertThat(edge.target()).isEqualTo(serviceFile.path());
            assertThat(edge.type()).isEqualTo("import");
            assertThat(edge.weight()).isEqualTo(3);
        });
    }

    @Test
    void calculatesUpstreamImpactFromDependencyGraph() {
        UUID projectId = UUID.randomUUID();
        SourceFileRecord controller = file(projectId, "src/UserController.java", "java",
                "import demo.UserService;\nclass UserController {}\n");
        SourceFileRecord serviceFile = file(projectId, "src/UserService.java", "java",
                "class UserService {}\n");
        when(files.listWithContent(projectId)).thenReturn(List.of(controller, serviceFile));

        ProjectAnalysisService.ImpactAnalysis impact = service.impact(projectId, serviceFile.path());

        assertThat(impact.dependents()).extracting(ProjectAnalysisService.ImpactNode::path)
                .contains(controller.path());
        assertThat(impact.score()).isGreaterThan(0);
    }

    private static SourceFileRecord file(UUID projectId, String path, String language, String content) {
        return new SourceFileRecord(UUID.randomUUID(), projectId, path, language, content,
                content.getBytes().length, content.lines().toList().size(), "sha");
    }
}
