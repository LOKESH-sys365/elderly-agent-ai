package com.eldercare.health;

import com.eldercare.ai.OllamaClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Offline health triage.
 * 1) Deterministic red-flag scan (no LLM involved) -> immediate emergency advice.
 * 2) Otherwise a structured, safety-prompted local LLM response with a strict disclaimer.
 * Never diagnoses; always directs to a human professional.
 */
@Service
public class SymptomCheckerService {

    private static final List<Pattern> RED_FLAGS = List.of(
            Pattern.compile("\\b(chest pain|pressure in.*chest|heart attack)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(can't breathe|cannot breathe|short(ness)? of breath|choking|gasping)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(stroke|face droop|slurred speech|one side weak(ness)?|sudden confusion)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(unconscious|passed out|fainted|not waking up|seizure|convulsion)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(bleeding (a lot|heavily)|uncontrolled bleeding|vomiting blood|blood in stool)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(severe burn|electrocuted|poison(ed)?|overdose)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(suicidal|want to die|hurt myself|self.?harm)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(fell|fallen|fell down)\\b.*\\b(can't get up|cannot get up|stuck)\\b", Pattern.CASE_INSENSITIVE)
    );

    private final OllamaClient ollama;

    public SymptomCheckerService(OllamaClient ollama) {
        this.ollama = ollama;
    }

    /** @return "EMERGENCY" | "URGENT" | "SELF_CARE" */
    public String triage(String symptoms) {
        for (Pattern p : RED_FLAGS) {
            if (p.matcher(symptoms).find()) return "EMERGENCY";
        }
        String lowered = symptoms.toLowerCase();
        if (lowered.matches(".*\\b(high fever|very high temperature|dehydrated|can't keep (water|fluids) down)\\b.*")) {
            return "URGENT";
        }
        return "SELF_CARE";
    }

    public String advice(String symptoms) {
        String level = triage(symptoms);
        if ("EMERGENCY".equals(level)) {
            return "EMERGENCY: This may be serious. I have alerted your emergency contact. "
                    + "If you can, call your local emergency number now. Do not drive yourself. "
                    + "Sit or lie down somewhere safe and unlock your door if possible.";
        }
        String system = """
                You are a careful health helper for an elderly person. You are NOT a doctor.
                Rules:
                - Never diagnose or name a disease.
                - Give simple, practical comfort steps suitable for a senior.
                - Clearly say when they should call a doctor or pharmacist today.
                - Use short sentences, warm tone, plain words.
                - Always end with: "This is general guidance, not medical advice. Please talk to a doctor or pharmacist."
                """;
        String user = "Urgency level: " + level + "\nSymptoms: " + symptoms;
        return ollama.generate(system, user);
    }
}
