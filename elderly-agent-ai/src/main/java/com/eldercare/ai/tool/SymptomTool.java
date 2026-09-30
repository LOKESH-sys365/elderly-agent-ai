package com.eldercare.ai.tool;

import com.eldercare.health.SymptomCheckerService;
import com.eldercare.user.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SymptomTool implements Tool {

    private final SymptomCheckerService checker;

    public SymptomTool(SymptomCheckerService checker) {
        this.checker = checker;
    }

    @Override
    public String name() { return "check_symptoms"; }

    @Override
    public String description() {
        return "Give safe general guidance when the user mentions feeling unwell, pain, fever, or symptoms. " +
               "Never gives a diagnosis; escalates emergencies.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "symptoms", Map.of("type", "string",
                                "description", "the symptoms the user described, in their own words")),
                "required", List.of("symptoms"));
    }

    @Override
    public String execute(User user, Map<String, Object> args) {
        Object s = args.get("symptoms");
        if (s == null || s.toString().isBlank()) return "No symptoms were provided.";
        return checker.advice(s.toString());
    }
}
