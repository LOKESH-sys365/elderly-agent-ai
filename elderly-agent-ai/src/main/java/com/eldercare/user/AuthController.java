package com.eldercare.user;

import com.eldercare.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank String role,           // SENIOR | CAREGIVER
            String displayName,
            String emergencyContact) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        User user = users.findByUsername(req.username())
                .filter(User::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        String token = jwt.generateToken(user.getUsername(), user.getRole().name());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "role", user.getRole().name(),
                "displayName", user.getDisplayName() == null ? user.getUsername() : user.getDisplayName()));
    }

    /** First-run bootstrap: create the senior account (and caregiver). Disabled once an ADMIN exists. */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        if (users.existsByUsername(req.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        Role role;
        try {
            role = Role.valueOf(req.role().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be SENIOR or CAREGIVER");
        }
        if (role == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot self-register as ADMIN");
        }
        User user = new User(req.username(), encoder.encode(req.password()), role,
                req.displayName() == null ? req.username() : req.displayName());
        user.setEmergencyContact(req.emergencyContact());
        users.save(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "User created", "username", user.getUsername()));
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) {
        User user = (User) auth.getPrincipal();
        return Map.of(
                "username", user.getUsername(),
                "role", user.getRole().name(),
                "displayName", user.getDisplayName() == null ? user.getUsername() : user.getDisplayName());
    }
}
