package dev.codeatlas.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class AgentController {
    private final AgentService service;

    public AgentController(AgentService service) {
        this.service = service;
    }

    @GetMapping("/projects/{projectId}/sessions")
    public List<AnalysisSession> sessions(@PathVariable UUID projectId) {
        return service.sessions(projectId);
    }

    @PostMapping("/projects/{projectId}/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public AnalysisSession createSession(@PathVariable UUID projectId,
                                         @Valid @RequestBody CreateSessionRequest request) {
        return service.createSession(projectId, request.title());
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public List<ChatMessage> messages(@PathVariable UUID sessionId) {
        return service.messages(sessionId);
    }

    @PostMapping("/sessions/{sessionId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessage ask(@PathVariable UUID sessionId, @Valid @RequestBody AskRequest request) {
        return service.ask(sessionId, request.content());
    }

    public record CreateSessionRequest(@Size(max = 200) String title) {
    }

    public record AskRequest(@NotBlank @Size(max = 8000) String content) {
    }
}
