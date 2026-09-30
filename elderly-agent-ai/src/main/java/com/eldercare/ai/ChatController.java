package com.eldercare.ai;

import com.eldercare.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final AgentService agent;

    public ChatController(AgentService agent) {
        this.agent = agent;
    }

    public record ChatRequest(
            @NotBlank @Size(max = 2000) String message) {}

    @PostMapping("/chat")
    public ResponseEntity<?> chat(Authentication auth, @Valid @RequestBody ChatRequest req) {
        User user = (User) auth.getPrincipal();
        AgentService.AgentReply reply = agent.chat(user, req.message().trim());
        return ResponseEntity.ok(Map.of(
                "reply", reply.answer(),
                "actions", reply.actions()));
    }
}
