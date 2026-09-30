package com.eldercare.call;

import com.eldercare.security.JwtService;
import com.eldercare.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebRTC signaling over the local network (fully offline).
 * Media flows peer-to-peer between the senior's device and the caregiver's
 * browser/phone on the same LAN; this handler only exchanges SDP/ICE and
 * call-control events (ring / accept / decline / hangup).
 *
 * Connect: ws://<host>:8080/ws/signal?token=<JWT>
 * Message : {"type":"offer|answer|ice|ring|accept|decline|hangup", "to":"<username>", "data":{...}}
 */
@Component
public class SignalingHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SignalingHandler.class);

    /** username -> its live sessions (a user may connect from several devices). */
    private final Map<String, Map<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final JwtService jwt;
    private final UserRepository users;

    public SignalingHandler(JwtService jwt, UserRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Optional<String> username = authenticate(session);
        if (username.isEmpty()) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid or expired token"));
            return;
        }
        sessions.computeIfAbsent(username.get(), k -> new ConcurrentHashMap<>())
                .put(session.getId(), session);
        log.info("Signaling connection established for {}", username.get());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String from = ownerOf(session);
        if (from == null) return;

        JsonNode node = mapper.readTree(message.getPayload());
        String type = node.path("type").asText("");
        String to = node.path("to").asText("");

        switch (type) {
            case "ring", "accept", "decline", "hangup", "offer", "answer", "ice" -> {
                if (to.isBlank()) return;
                forward(to, mapper.createObjectNode()
                        .put("type", type)
                        .put("from", from)
                        .set("data", node.path("data")));
                if ("ring".equals(type)) {
                    log.info("Call: {} is ringing {}", from, to);
                }
            }
            default -> log.warn("Unknown signaling message type '{}' from {}", type, from);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String owner = ownerOf(session);
        if (owner != null) {
            Map<String, WebSocketSession> userSessions = sessions.get(owner);
            if (userSessions != null) {
                userSessions.remove(session.getId());
                if (userSessions.isEmpty()) sessions.remove(owner);
            }
        }
    }

    /** Server-initiated event, e.g. emergency alert fan-out or incoming-call notification. */
    public void push(String username, String type, JsonNode data) {
        Map<String, WebSocketSession> userSessions = sessions.get(username);
        if (userSessions == null) return;
        var payload = mapper.createObjectNode().put("type", type).set("data", data);
        for (WebSocketSession s : userSessions.values()) {
            try {
                synchronized (s) {
                    if (s.isOpen()) s.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
                }
            } catch (IOException e) {
                log.warn("Failed to push {} to {}", type, username);
            }
        }
    }

    public boolean isOnline(String username) {
        Map<String, WebSocketSession> s = sessions.get(username);
        return s != null && s.values().stream().anyMatch(WebSocketSession::isOpen);
    }

    private void forward(String to, JsonNode payload) {
        Map<String, WebSocketSession> userSessions = sessions.get(to);
        if (userSessions == null) return;
        for (WebSocketSession s : userSessions.values()) {
            try {
                synchronized (s) {
                    if (s.isOpen()) s.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
                }
            } catch (IOException e) {
                log.warn("Failed to forward signaling message to {}", to);
            }
        }
    }

    private Optional<String> authenticate(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null || uri.getQuery() == null) return Optional.empty();
            for (String param : uri.getQuery().split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && kv[0].equals("token")) {
                    String token = java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
                    String username = jwt.extractUsername(token);
                    if (users.findByUsername(username).isPresent()) {
                        return Optional.of(username);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Signaling handshake authentication failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private String ownerOf(WebSocketSession session) {
        return sessions.entrySet().stream()
                .filter(e -> e.getValue().containsKey(session.getId()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}
