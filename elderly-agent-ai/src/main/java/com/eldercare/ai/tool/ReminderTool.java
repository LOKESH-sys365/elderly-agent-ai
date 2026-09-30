package com.eldercare.ai.tool;

import com.eldercare.reminder.ReminderService;
import com.eldercare.reminder.ReminderType;
import com.eldercare.user.User;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
public class ReminderTool implements Tool {

    private final ReminderService reminders;

    public ReminderTool(ReminderService reminders) {
        this.reminders = reminders;
    }

    @Override
    public String name() { return "manage_reminder"; }

    @Override
    public String description() {
        return "Create, list, or complete reminders and medication schedules for the user. " +
               "Use when the user asks to remember something, take pills at a time, or asks what is coming up.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "action", Map.of("type", "string", "enum", List.of("create", "list", "complete"),
                                "description", "create a new reminder, list upcoming ones, or mark one complete"),
                        "title", Map.of("type", "string", "description", "short plain-language reminder title"),
                        "time", Map.of("type", "string",
                                "description", "time in 24h HH:mm (or 'now'); combined with today unless daily=true"),
                        "reminder_type", Map.of("type", "string",
                                "enum", List.of("MEDICATION", "APPOINTMENT", "GENERAL")),
                        "daily", Map.of("type", "boolean", "description", "repeat every day"),
                        "reminder_id", Map.of("type", "integer", "description", "id from a previous list, for complete")),
                "required", List.of("action"));
    }

    @Override
    public String execute(User user, Map<String, Object> args) {
        String action = str(args, "action");
        return switch (action) {
            case "list" -> reminders.listForAgent(user);
            case "create" -> create(user, args);
            case "complete" -> {
                Object id = args.get("reminder_id");
                if (id == null) yield "Missing reminder_id.";
                try {
                    reminders.complete(user, Long.parseLong(id.toString()));
                    yield "Reminder marked complete.";
                } catch (Exception e) {
                    yield "Could not complete reminder: " + e.getMessage();
                }
            }
            default -> "Unknown action '" + action + "'. Use create, list, or complete.";
        };
    }

    private String create(User user, Map<String, Object> args) {
        String title = str(args, "title");
        if (title == null || title.isBlank() || title.length() > 200) {
            return "Title is missing or too long.";
        }
        String timeStr = str(args, "time");
        LocalDateTime when;
        if (timeStr == null || timeStr.equalsIgnoreCase("now")) {
            when = LocalDateTime.now().plusMinutes(1);
        } else {
            try {
                when = LocalDateTime.of(LocalDate.now(), LocalTime.parse(timeStr.trim()));
                if (when.isBefore(LocalDateTime.now())) when = when.plusDays(1);
            } catch (Exception e) {
                return "I could not understand the time '" + timeStr + "'. Please retry with HH:mm.";
            }
        }
        ReminderType type = ReminderType.GENERAL;
        String t = str(args, "reminder_type");
        if (t != null) {
            try { type = ReminderType.valueOf(t.toUpperCase()); } catch (IllegalArgumentException ignored) {}
        }
        boolean daily = Boolean.TRUE.equals(args.get("daily"));
        var saved = reminders.create(user, title.trim(), when, type, daily, null);
        return "Reminder created (id=" + saved.getId() + "): \"" + saved.getTitle() + "\" at "
                + saved.getRemindAt().format(DateTimeFormatter.ofPattern("h:mm a"))
                + (daily ? ", every day." : ".");
    }

    private String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? null : v.toString();
    }
}
