package com.eldercare.emergency;

import com.eldercare.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SOS / fall-detection alerting.
 * Fully offline behaviour: persist the alert, notify connected caregivers over the
 * local WebSocket, and (optionally) trigger a dial-out via a locally attached
 * GSM modem or SIP gateway. No cloud services are used.
 */
@Service
public class EmergencyService {

    private static final Logger log = LoggerFactory.getLogger(EmergencyService.class);

    private final StringRedisTemplate redis;
    private final String contactNumber;
    private final Map<String, Instant> recentAlerts = new ConcurrentHashMap<>();

    public EmergencyService(StringRedisTemplate redis,
                            @Value("${emergency.contact-number}") String contactNumber) {
        this.redis = redis;
        this.contactNumber = contactNumber;
    }

    /** Deduplicates SOS storms (e.g. a confused repeated press) within 2 minutes. */
    public boolean trigger(User user, String reason) {
        String key = "sos:" + user.getUsername();
        if (redis.hasKey(key)) {
            return false; // already active
        }
        redis.opsForValue().set(key, reason == null ? "SOS" : reason, Duration.ofMinutes(2));
        redis.opsForList().leftPush("sos:log:" + user.getUsername(),
                Instant.now() + " | " + (reason == null ? "SOS" : reason));

        notifyCaregivers(user, reason);
        dialOut(user);
        return true;
    }

    @Async
    void notifyCaregivers(User user, String reason) {
        log.warn("EMERGENCY for {} ({}) reason={}. Caregivers connected over LAN should be alerted now.",
                user.getDisplayName(), user.getUsername(), reason);
        // WebSocket fan-out handled by AlertWebSocketHandler (see call package).
    }

    /**
     * Optional dial-out hook. In an offline deployment this can shell out to a
     * USB GSM modem (e.g. `gammu sendsms`) or a local SIP client.
     * Disabled unless eldercare.dialout.enabled=true.
     */
    @Async
    void dialOut(User user) {
        if (!Boolean.getBoolean("eldercare.dialout.enabled")) {
            log.info("Dial-out disabled. Would call {} for user {}", contactNumber, user.getUsername());
            return;
        }
        try {
            Process p = new ProcessBuilder("gammu", "sendsms", "TEXT", contactNumber,
                    "-text", "EMERGENCY from " + user.getDisplayName()).start();
            p.waitFor();
        } catch (Exception e) {
            log.error("Dial-out failed", e);
        }
    }
}
