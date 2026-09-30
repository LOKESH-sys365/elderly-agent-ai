package com.eldercare.call;

import com.eldercare.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/calls")
public class CallController {

    private final CallService callService;

    public CallController(CallService callService) {
        this.callService = callService;
    }

    @PostMapping("/start")
    public ResponseEntity<?> start(Authentication auth,
                                   @RequestBody(required = false) Map<String, String> body) {
        User user = (User) auth.getPrincipal();
        String message = body != null ? body.get("message") : null;
        String result = callService.initiateCall(user, message);
        return ResponseEntity.ok(Map.of("message", result));
    }
}
