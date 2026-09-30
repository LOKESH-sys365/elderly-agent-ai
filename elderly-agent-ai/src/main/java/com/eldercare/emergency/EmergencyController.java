package com.eldercare.emergency;

import com.eldercare.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/emergency")
public class EmergencyController {

    private final EmergencyService service;

    public EmergencyController(EmergencyService service) {
        this.service = service;
    }

    /** Big red button endpoint. Also reachable by the agent tool when the user says "I fell". */
    @PostMapping("/sos")
    public ResponseEntity<?> sos(Authentication auth,
                                 @RequestBody(required = false) Map<String, String> body) {
        User user = (User) auth.getPrincipal();
        String reason = body != null ? body.getOrDefault("reason", "SOS button pressed") : "SOS button pressed";
        boolean accepted = service.trigger(user, reason);
        return ResponseEntity.status(accepted ? 200 : 429)
                .body(Map.of("accepted", accepted,
                        "message", accepted
                                ? "Help is being alerted. Stay calm."
                                : "An alert is already active. Help is on the way."));
    }
}
