package com.eldercare.reminder;

import com.eldercare.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {

    private final ReminderService service;

    public ReminderController(ReminderService service) {
        this.service = service;
    }

    public record CreateRequest(
            @NotBlank String title,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime remindAt,
            String type,
            boolean recurringDaily,
            String notes) {}

    @GetMapping
    public List<Reminder> upcoming(Authentication auth) {
        return service.upcoming(current(auth));
    }

    @GetMapping("/today")
    public List<Reminder> today(Authentication auth) {
        return service.today(current(auth));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Reminder create(Authentication auth, @Valid @RequestBody CreateRequest req) {
        ReminderType type = ReminderType.GENERAL;
        if (req.type() != null) {
            try { type = ReminderType.valueOf(req.type().toUpperCase()); }
            catch (IllegalArgumentException ignored) { /* defaults to GENERAL */ }
        }
        return service.create(current(auth), req.title(), req.remindAt(), type, req.recurringDaily(), req.notes());
    }

    @PostMapping("/{id}/complete")
    public Reminder complete(Authentication auth, @PathVariable Long id) {
        return service.complete(current(auth), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable Long id) {
        service.delete(current(auth), id);
    }

    private User current(Authentication auth) {
        return (User) auth.getPrincipal();
    }
}
