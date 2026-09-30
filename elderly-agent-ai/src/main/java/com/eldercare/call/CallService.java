package com.eldercare.call;

import com.eldercare.user.User;
import com.eldercare.user.UserRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Starts a family video call. The senior's device and the caregiver's device
 * rendezvous over the LAN through the signaling WebSocket; audio/video is
 * peer-to-peer WebRTC, so no internet is ever needed.
 */
@Service
public class CallService {

    private static final Logger log = LoggerFactory.getLogger(CallService.class);
    private static final Duration RING_TTL = Duration.ofSeconds(45);

    private final SignalingHandler signaling;
    private final UserRepository users;
    private final StringRedisTemplate redis;
    private final String caregiverUsername;

    public CallService(SignalingHandler signaling, UserRepository users, StringRedisTemplate redis,
                       @Value("${emergency.caregiver-user}") String caregiverUsername) {
        this.signaling = signaling;
        this.users = users;
        this.redis = redis;
        this.caregiverUsername = caregiverUsername;
    }

    /** @return human-readable outcome string for the agent/LLM. */
    public String initiateCall(User caller, String message) {
        String callee = resolveCaregiver(caller);
        if (callee == null) {
            return "No family member is configured to call. A caregiver account should be set up first.";
        }
        String callId = UUID.randomUUID().toString().substring(0, 8);
        redis.opsForValue().set("call:" + callId, caller.getUsername() + "->" + callee, RING_TTL);

        var payload = JsonNodeFactory.instance.objectNode()
                .put("callId", callId)
                .put("from", caller.getUsername())
                .put("fromName", caller.getDisplayName() == null ? caller.getUsername() : caller.getDisplayName())
                .put("message", message == null ? "" : message);
        signaling.push(callee, "ring", payload);

        boolean online = signaling.isOnline(callee);
        log.info("Call {} initiated: {} -> {} (callee online: {})", callId, caller.getUsername(), callee, online);
        return online
                ? "The video call to " + callee + " is ringing now."
                : "The call was requested. " + callee + "'s device is not currently connected, but they will see the missed call.";
    }

    /** Seniors call their caregiver; caregivers call the senior. Simple and predictable. */
    private String resolveCaregiver(User caller) {
        Set<String> all = new java.util.HashSet<>();
        users.findAll().forEach(u -> all.add(u.getUsername()));
        if (all.contains(caregiverUsername) && !caller.getUsername().equals(caregiverUsername)) {
            return caregiverUsername;
        }
        return users.findAll().stream()
                .map(User::getUsername)
                .filter(n -> !n.equals(caller.getUsername()))
                .findFirst()
                .orElse(null);
    }
}
