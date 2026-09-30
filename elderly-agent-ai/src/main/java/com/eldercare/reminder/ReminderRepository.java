package com.eldercare.reminder;

import com.eldercare.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {
    List<Reminder> findByUserAndCompletedFalse(User user);
    List<Reminder> findByRemindAtBeforeAndCompletedFalse(LocalDateTime time);
    List<Reminder> findByUserAndRemindAtBetween(User user, LocalDateTime from, LocalDateTime to);
}
