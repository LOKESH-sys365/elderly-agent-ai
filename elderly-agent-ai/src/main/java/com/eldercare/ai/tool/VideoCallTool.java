package com.eldercare.ai.tool;

import com.eldercare.user.User;
import com.eldercare.call.CallService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class VideoCallTool implements Tool {

    private final CallService calls;

    public VideoCallTool(CallService calls) {
        this.calls = calls;
    }

    @Override
    public String name() { return "start_family_call"; }

    @Override
    public String description() {
        return "Start a video call with the user's family/caregiver over the local network. " +
               "Use when the user asks to call, see, or talk to their family member.";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "message", Map.of("type", "string",
                                "description", "optional short message to send with the call")),
                "required", List.of());
    }

    @Override
    public String execute(User user, Map<String, Object> args) {
        String message = args.get("message") == null ? null : args.get("message").toString();
        String result = calls.initiateCall(user, message);
        return result + " Tell the user: their family has been called, the screen will connect shortly, " +
               "and they should just speak normally once it connects.";
    }
}
