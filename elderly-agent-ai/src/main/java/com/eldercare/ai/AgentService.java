package com.eldercare.ai;

import com.eldercare.ai.tool.Tool;
import com.eldercare.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agentic loop: sends the user's message to the local LLM together with tool
 * definitions, executes any requested tools (reminders, emergency, symptoms,
 * family calls), feeds results back, and returns the final plain-language answer.
 * Conversation history lives in Redis (dual-DB design: MySQL = durable data,
 * Redis = transient state).
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final int MAX_TOOL_ITERATIONS = 5;
    private static final int HISTORY_TURNS = 10;
    private static final Duration HISTORY_TTL = Duration.ofHours(24);

    private final OllamaClient ollama;
    private final List<Tool> tools;
    private final RedisTemplate<String, Object> redis;
    private final ObjectMapper mapper = new ObjectMapper();

    public AgentService(OllamaClient ollama, List<Tool> tools, RedisTemplate<String, Object> redis) {
        this.ollama = ollama;
        this.tools = tools;
        this.redis = redis;
    }

    public record AgentReply(String answer, List<String> actions) {}

    public AgentReply chat(User user, String userText) {
        List<Map<String, Object>> history = loadHistory(user);
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt(user)));
        messages.addAll(history);
        messages.add(Map.of("role", "user", "content", userText));

        List<String> actions = new ArrayList<>();

        for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
            OllamaClient.ChatResponse response = ollama.chat(messages, toolDefinitions());
            OllamaClient.Message msg = response.message();
            messages.add(stripImages(msg));

            List<Map<String, Object>> toolCalls = msg.tool_calls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                String answer = msg.content() == null ? "I'm sorry, I didn't catch that. Could you say it again?"
                        : msg.content().trim();
                saveHistory(user, userText, answer);
                return new AgentReply(answer, actions);
            }

            for (Map<String, Object> call : toolCalls) {
                @SuppressWarnings("unchecked")
                Map<String, Object> function = (Map<String, Object>) call.get("function");
                if (function == null) continue;
                String name = String.valueOf(function.get("name"));
                Map<String, Object> args = parseArgs(function.get("arguments"));
                log.info("Agent tool call: {} {} by user {}", name, args, user.getUsername());

                String result = tools.stream()
                        .filter(t -> t.name().equals(name))
                        .findFirst()
                        .map(t -> safeExecute(t, user, args))
                        .orElse("Tool '" + name + "' is not available.");

                actions.add(name);
                messages.add(Map.of("role", "tool", "content", result));
            }
        }
        String fallback = "Let me get that sorted out. Could you tell me a little more?";
        saveHistory(user, userText, fallback);
        return new AgentReply(fallback, actions);
    }

    private String systemPrompt(User user) {
        String name = user.getDisplayName() == null ? "friend" : user.getDisplayName();
        return """
                You are Maya, a warm, patient voice assistant for %s, an elderly person.
                - Speak in short, simple sentences. One idea at a time.
                - Be kind and reassuring. Never use technical jargon.
                - If the user mentions falling, chest pain, trouble breathing, or asks for urgent help,
                  use the trigger_emergency tool immediately.
                - If the user mentions feeling unwell, use check_symptoms.
                - If the user asks about their schedule, pills, or appointments, use manage_reminder.
                - If the user asks to talk to or see family, use start_family_call.
                - You run fully offline on their own device. Never claim to browse the internet.
                - If you don't understand, gently ask them to repeat.
                """.formatted(name);
    }

    private List<Map<String, Object>> toolDefinitions() {
        return tools.stream()
                .map(t -> Map.<String, Object>of(
                        "type", "function",
                        "function", Map.of(
                                "name", t.name(),
                                "description", t.description(),
                                "parameters", t.parameters())))
                .toList();
    }

    private Map<String, Object> parseArgs(Object raw) {
        try {
            if (raw instanceof Map<?, ?> m) return mapper.convertValue(m, new TypeReference<>() {});
            if (raw instanceof String s && !s.isBlank()) {
                return mapper.readValue(s, new TypeReference<>() {});
            }
        } catch (Exception e) {
            log.warn("Could not parse tool arguments: {}", raw);
        }
        return new HashMap<>();
    }

    private String safeExecute(Tool tool, User user, Map<String, Object> args) {
        try {
            return tool.execute(user, args);
        } catch (Exception e) {
            log.error("Tool {} failed for user {}", tool.name(), user.getUsername(), e);
            return "The action could not be completed. Please try again or ask a caregiver for help.";
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadHistory(User user) {
        Object raw = redis.opsForValue().get(historyKey(user));
        if (raw instanceof List<?> list) return new ArrayList<>((List<Map<String, Object>>) list);
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private void saveHistory(User user, String userText, String answer) {
        List<Map<String, Object>> history = loadHistory(user);
        history.add(Map.of("role", "user", "content", userText));
        history.add(Map.of("role", "assistant", "content", answer));
        int max = HISTORY_TURNS * 2;
        if (history.size() > max) {
            history = new ArrayList<>(history.subList(history.size() - max, history.size()));
        }
        redis.opsForValue().set(historyKey(user), history, HISTORY_TTL);
    }

    private String historyKey(User user) {
        return "conv:" + user.getUsername();
    }

    private Map<String, Object> stripImages(OllamaClient.Message msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("role", msg.role());
        m.put("content", msg.content() == null ? "" : msg.content());
        if (msg.tool_calls() != null) m.put("tool_calls", msg.tool_calls());
        return m;
    }
}
