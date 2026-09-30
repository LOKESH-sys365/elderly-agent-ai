package com.eldercare.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Minimal typed client for the local Ollama /api/chat endpoint.
 * Everything runs on localhost - no data ever leaves the machine.
 */
@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final RestClient rest;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();

    public OllamaClient(@Value("${ollama.base-url}") String baseUrl,
                        @Value("${ollama.model}") String model,
                        @Value("${ollama.chat-timeout-seconds}") long timeoutSeconds) {
        this.model = model;
        this.rest = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
                    setReadTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
                }})
                .build();
    }

    public ChatResponse chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        Map<String, Object> body = mapper.convertValue(Map.of(
                "model", model,
                "messages", messages,
                "tools", tools,
                "stream", false,
                "options", Map.of("temperature", 0.3)
        ), Map.class);

        ChatResponse response = rest.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(ChatResponse.class);

        if (response == null || response.message() == null) {
            throw new IllegalStateException("Empty response from Ollama");
        }
        return response;
    }

    /** Simple single-turn generation (used by the symptom checker and voice). */
    public String generate(String systemPrompt, String userPrompt) {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt));
        return chat(messages, List.of()).message().content().trim();
    }

    public String getModel() { return model; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatResponse(boolean done, Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String role, String content,
                          List<Map<String, Object>> tool_calls, List<String> images) {}
}
