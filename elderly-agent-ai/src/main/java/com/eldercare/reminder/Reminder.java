package com.eldercare.reminder;

import com.eldercare.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Entity
@Table(name = "reminders")
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotBlank
    @Column(nullable = false, length = 200)
    private String title; // e.g. "Take blood pressure pill"

    @Column(length = 500)
    private String notes;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime remindAt;

    private boolean recurringDaily = false;
    private boolean completed = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReminderType type = ReminderType.GENERAL;

    protected Reminder() {}

    public Reminder(User user, String title, LocalDateTime remindAt, ReminderType type) {
        this.user = user;
        this.title = title;
        this.remindAt = remindAt;
        this.type = type;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getTitle() { return title; }
    public String getNotes() { return notes; }
    public LocalDateTime getRemindAt() { return remindAt; }
    public boolean isRecurringDaily() { return recurringDaily; }
    public boolean isCompleted() { return completed; }
    public ReminderType getType() { return type; }

    public void setNotes(String notes) { this.notes = notes; }
    public void setRecurringDaily(boolean recurringDaily) { this.recurringDaily = recurringDaily; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public void setRemindAt(LocalDateTime remindAt) { this.remindAt = remindAt; }
}
