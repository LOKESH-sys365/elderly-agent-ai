package com.eldercare.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

/**
 * Bridges to the offline voice sidecar (scripts/voice_sidecar.py):
 *   - Speech-to-text: Vosk (runs locally, no cloud)
 *   - Text-to-speech: Piper (fast neural TTS, CPU-friendly)
 * The sidecar listens on localhost only - audio never leaves the machine.
 */
@Service
public class VoiceService {

    private static final Logger log = LoggerFactory.getLogger(VoiceService.class);

    private final RestClient rest;

    public VoiceService(@Value("${voice.sidecar-url}") String sidecarUrl) {
        this.rest = RestClient.builder()
                .baseUrl(sidecarUrl)
                .requestFactory(new SimpleClientHttpRequestFactory() {{
                    setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
                    setReadTimeout((int) Duration.ofSeconds(60).toMillis());
                }})
                .build();
    }

    /** @return transcribed text, or throws if the sidecar is unavailable. */
    public String speechToText(MultipartFile audio) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(audio.getBytes()) {
                @Override
                public String getFilename() { return "audio.wav"; }
            });
            String text = rest.post()
                    .uri("/stt")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return text == null ? "" : text.trim();
        } catch (Exception e) {
            log.error("STT failed - is voice_sidecar.py running? ({})", e.getMessage());
            throw new VoiceUnavailableException("Speech recognition is not available right now.");
        }
    }

    /** @return WAV audio bytes of the spoken reply. */
    public byte[] textToSpeech(String text) {
        try {
            byte[] audio = rest.post()
                    .uri("/tts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"text\":\"" + text.replace("\"", "'")
                            .replace("\n", " ").replace("\r", " ") + "\"}")
                    .retrieve()
                    .body(byte[].class);
            return audio == null ? new byte[0] : audio;
        } catch (Exception e) {
            log.error("TTS failed - is voice_sidecar.py running? ({})", e.getMessage());
            throw new VoiceUnavailableException("Voice output is not available right now.");
        }
    }

    public static class VoiceUnavailableException extends RuntimeException {
        public VoiceUnavailableException(String msg) { super(msg); }
    }
}
