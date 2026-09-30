package com.eldercare.ai.tool;

import com.eldercare.user.User;

import java.util.Map;

/** One capability the agent can invoke. name/description/parameters describe it to Ollama. */
public interface Tool {
    String name();
    String description();
    /** JSON-schema-ish parameter description for the LLM. */
    Map<String, Object> parameters();
    /** Execute the tool for the given user. Must be side-effect safe (validate inputs). */
    String execute(User user, Map<String, Object> args);
}
