# Elderly Agent AI (Maya)

An **offline-first, voice-first agentic AI assistant for elderly people** who don't know
how to use technology. Big buttons, spoken replies, no jargon.

Built with **Spring Boot 3 (Java 17)**, **dual database: MySQL (durable data) + Redis
(sessions, conversation history, rate limiting)**, and a **local LLM via Ollama**.
Everything runs on the machine — no cloud, no external API calls.

## Features

| Feature | How it works |
|---|---|
| Voice conversation | Hold-to-talk → Vosk STT (offline) → local LLM → Piper TTS (offline) |
| Agentic actions | Tool loop: reminders/medication, symptom checker, family call, emergency SOS |
| Medication & reminders | MySQL-backed; daily reminders auto-roll over; agent can create/list/complete them by voice |
| Health symptom checker | Deterministic red-flag scan (no LLM) + safe LLM guidance; never diagnoses |
| Family video call | WebRTC peer-to-peer over the home LAN; Spring WebSocket only exchanges signaling |
| Emergency SOS | Big red button + voice ("I fell"); dedup, caregiver notify, optional GSM dial-out |
| Security | JWT + roles (SENIOR/CAREGIVER/ADMIN), BCrypt(12), Redis rate limiting, strict CORS, CSP headers |

## Architecture

```
Senior's device (browser)                Caregiver device (browser/phone)
        |                                         |
        v                                         v
+----------------------------------------------------------+
|  Spring Boot app (localhost:8080)                        |
|  - REST API (JWT secured)                                |
|  - WebSocket /ws/signal (JWT handshake, WebRTC signaling)|
|  - Agent loop -> Ollama (localhost:11434)                |
+-----+---------------+---------------+--------------------+
      |               |               |
      v               v               v
   MySQL 8          Redis 7        Voice sidecar (127.0.0.1:5055)
 (users,      (rate limits,      Vosk STT + Piper TTS
 reminders)   conv history,      (localhost only)
              SOS state)
```

Media in video calls flows **directly peer-to-peer** between devices on the same LAN.
Audio sent to the voice sidecar never leaves `127.0.0.1`.

## Prerequisites

- Java 17+, Maven 3.9+
- Docker (for MySQL + Redis) or local installs
- [Ollama](https://ollama.com) — CPU install is fine
- Python 3.10+ (for the voice sidecar)
- Piper TTS + a Vosk model (offline downloads, one time)

## Setup

```bash
# 1. Databases
docker compose up -d          # MySQL 3306, Redis 6379

# 2. Local LLM (CPU-friendly 3B model)
ollama pull llama3.2:3b

# 3. Voice sidecar
pip install -r scripts/requirements.txt
# Download: Vosk model  -> https://alphacephei.com/vosk/models  (vosk-model-small-en-us-0.15)
# Download: Piper voice -> https://github.com/rhasspy/piper (en_US-lessac-medium.onnx)
python scripts/voice_sidecar.py \
    --model /path/to/vosk-model-small-en-us-0.15 \
    --voice /path/to/en_US-lessac-medium.onnx \
    --piper /path/to/piper

# 4. Run the app
./mvnw spring-boot:run        # or: mvn spring-boot:run
```

Open **http://localhost:8080**.

## First run

1. Register accounts (or via curl):
   ```bash
   curl -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' -d '{
     "username":"grandma","password":"a-long-safe-password",
     "role":"SENIOR","displayName":"Grandma","emergencyContact":"+1..."}'
   curl -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' -d '{
     "username":"caregiver","password":"a-long-safe-password",
     "role":"CAREGIVER","displayName":"Sarah"}'
   ```
   The caregiver username must match `emergency.caregiver-user` (default `caregiver`).
2. Sign in on the senior's device as `grandma`.
3. On the caregiver's device, sign in as `caregiver` and open the same page —
   it keeps a WebSocket open so calls and SOS alerts arrive instantly.
4. Hold the blue button and talk: *"Remind me to take my blood pressure pill at 8 am every day."*

## Security checklist (before real use)

- [ ] Override every secret via environment variables:
      `JWT_SECRET` (256-bit), `MYSQL_PASSWORD`, `REDIS_PASSWORD`, `EMERGENCY_CONTACT`
- [ ] Serve over HTTPS if accessed from other devices (`server.port` behind nginx/caddy, or import a cert)
- [ ] Restrict MySQL/Redis ports to localhost or the LAN (`bind-address`, firewall)
- [ ] Create only the needed accounts; ADMIN exists for future admin tooling
- [ ] The voice sidecar binds to `127.0.0.1` by design — keep it that way
- [ ] Optional GSM dial-out: start with `-Deldercare.dialout.enabled=true` and `gammu` installed

## Key endpoints

| Method & path | Purpose |
|---|---|
| `POST /api/auth/register`, `/api/auth/login` | Accounts, JWT issuance |
| `POST /api/chat` | Text chat with the agent (`{message}`) |
| `POST /api/voice/talk` | Push-to-talk: audio in → spoken WAV out |
| `GET/POST/DELETE /api/reminders[/...]` | Reminder CRUD |
| `POST /api/emergency/sos` | SOS trigger |
| `POST /api/calls/start` | Ring the caregiver |
| `WS /ws/signal?token=JWT` | WebRTC signaling + ring/SOS push |
| `GET /` | Senior UI |

## Notes

- The UI records WebM audio; the sidecar decodes WAV — for best accuracy record
  16 kHz mono WAV (or adapt `voice_sidecar.py` with ffmpeg piping).
- Conversation history is capped (10 turns) and TTL'd (24 h) in Redis.
- The LLM is prompted to escalate emergencies via the `trigger_emergency` tool;
  the red-flag scanner in `SymptomCheckerService` runs **before** the LLM so a
  confused model can never delay an emergency escalation.
