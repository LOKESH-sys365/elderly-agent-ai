package com.eldercare.voice;

import com.eldercare.ai.AgentService;
import com.eldercare.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private final VoiceService voice;
    private final AgentService agent;

    public VoiceController(VoiceService voice, AgentService agent) {
        this.voice = voice;
        this.agent = agent;
    }

    /** Push-to-talk: upload audio, get the AI's spoken answer back as WAV. */
    @PostMapping("/talk")
    public ResponseEntity<?> talk(Authentication auth,
                                  @RequestParam("audio") MultipartFile audio,
                                  @RequestParam(value = "speak", defaultValue = "true") boolean speak) {
        if (audio.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No audio received");
        }
        User user = (User) auth.getPrincipal();
        String text = voice.speechToText(audio);
        if (text.isBlank()) {
            return ResponseEntity.ok(Map.of(
                    "transcript", "",
                    "reply", "I didn't hear anything. Please press the button and speak again."));
        }
        AgentService.AgentReply reply = agent.chat(user, text);
        if (!speak) {
            return ResponseEntity.ok(Map.of("transcript", text, "reply", reply.answer(),
                    "actions", reply.actions()));
        }
        byte[] wav = voice.textToSpeech(reply.answer());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "audio/wav")
                .header("X-Transcript", encode(text))
                .body(wav);
    }

    /** Text in -> spoken WAV out (used to read reminders/alerts aloud). */
    @PostMapping("/tts")
    public ResponseEntity<byte[]> tts(@Valid @RequestBody TtsRequest req) {
        byte[] wav = voice.textToSpeech(req.text());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "audio/wav")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(wav);
    }

    public record TtsRequest(@NotBlank String text) {}

    private String encode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
