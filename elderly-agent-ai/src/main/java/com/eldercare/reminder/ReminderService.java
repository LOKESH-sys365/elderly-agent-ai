package com.eldercare.reminder;

import com.eldercare.user.User;
import com.eldercare.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("h:mm a 'on' EEEE");

    private final ReminderRepository reminders;
    private final UserRepository users;

    public ReminderService(ReminderRepository reminders, UserRepository users) {
        this.reminders = reminders;
        this.users = users;
    }

    @Transactional
    public Reminder create(User user, String title, LocalDateTime remindAt, ReminderType type,
                           boolean recurringDaily, String notes) {
        Reminder r = new Reminder(user, title, remindAt, type);
        r.setRecurringDaily(recurringDaily);
        r.setNotes(notes);
        return reminders.save(r);
    }

    @Transactional
    public Reminder complete(User user, Long id) {
        Reminder r = reminders.findById(id)
                .filter(x -> x.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reminder not found"));
        r.setCompleted(true);
        return r;
    }

    @Transactional
    public void delete(User user, Long id) {
        Reminder r = reminders.findById(id)
                .filter(x -> x.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reminder not found"));
        reminders.delete(r);
    }

    public List<Reminder> upcoming(User user) {
        return reminders.findByUserAndCompletedFalse(user);
    }

    public List<Reminder> today(User user) {
        LocalDateTime start = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        return reminders.findByUserAndRemindAtBetween(user, start, start.plusDays(1));
    }

    /** LLM-facing tool: list pending reminders as plain text. */
    public String listForAgent(User user) {
        List<Reminder> list = upcoming(user);
        if (list.isEmpty()) return "No upcoming reminders.";
        StringBuilder sb = new StringBuilder("Upcoming reminders:\n");
        list.forEach(r -> sb.append("- [id=").append(r.getId()).append("] ")
                .append(r.getTitle())
                .append(r.isRecurringDaily() ? " (daily)" : "")
                .append(" at ").append(r.getRemindAt().format(FMT)).append('\n'));
        return sb.toString();
    }

    /** Runs every 30 seconds; daily reminders roll over to the next day once due. */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void rollOverDailyReminders() {
        LocalDateTime now = LocalDateTime.now();
        for (Reminder r : reminders.findByRemindAtBeforeAndCompletedFalse(now)) {
            if (r.isRecurringDaily()) {
                r.setRemindAt(now.plusDays(1).withHour(r.getRemindAt().getHour())
                        .withMinute(r.getRemindAt().getMinute()).withSecond(0));
                log.info("Rolled over daily reminder '{}' for user {}", r.getTitle(), r.getUser().getUsername());
            }
        }
    }
}
