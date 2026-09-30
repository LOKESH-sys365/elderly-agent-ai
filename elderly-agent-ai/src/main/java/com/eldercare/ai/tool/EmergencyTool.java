package com.eldercare.ai.tool;

import com.eldercare.emergency.EmergencyService;
import com.eldercare.user.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Allows the agent to trigger an SOS when the user says things like
 * "I fell down", "I can't breathe", "help me now".
 */
@Component
public class EmergencyTool implements Tool {

    private final EmergencyService emergency;

    public EmergencyTool(EmergencyService emergency) {
        this.emergency = emergency;
    }

    @Override
    public String name() { return "trigger_emergency"; }

    @Override
    public String description() {
        return "IMMEDIATELY alert emergency contacts when the user reports a fall, chest pain, " +
               "breathing trouble, severe injury, or asks for urgent help. Do NOT use for normal questions.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "reason", Map.of("type", "string",
                                "description", "short description of the emergency, e.g. 'user fell in the bathroom'")),
                "required", List.of("reason"));
    }

    @Override
    public String execute(User user, Map<String, Object> args) {
        String reason = args.get("reason") == null ? "User asked for emergency help"
                : args.get("reason").toString();
        boolean accepted = emergency.trigger(user, reason);
        return accepted
                ? "Emergency alert sent to caregivers. Tell the user to stay calm and that help has been alerted."
                : "An emergency alert is already active. Tell the user help is already on the way.";
    }
}
