#!/usr/bin/env python3
"""
Offline voice sidecar for the Elderly Agent AI.
Runs on localhost only. No network calls, no cloud APIs.

- STT: Vosk (small English model recommended for CPU: vosk-model-small-en-us-0.15)
- TTS: Piper (e.g. en_US-lessac-medium)

Install:
    pip install -r requirements.txt
    # download a Vosk model:  https://alphacephei.com/vosk/models
    # download a Piper voice: https://github.com/rhasspy/piper/blob/master/VOICES.md

Run:
    python voice_sidecar.py --model /path/to/vosk-model --piper /path/to/piper --voice en_US-lessac-medium

Then:  POST /stt  (multipart file, 16kHz mono WAV)
       POST /tts  (JSON {"text": "..."})  -> WAV
"""
import argparse
import io
import json
import wave

from flask import Flask, request, send_file
from vosk import Model, KaldiRecognizer, SetLogLevel

SetLogLevel(-1)
app = Flask(__name__)

model = None
piper_cmd = None


def init(args):
    global model, piper_cmd
    model = Model(args.model)
    piper_cmd = [args.piper, "--model", args.voice, "--output_file", "/dev/stdout", "--stdin-file", "-"]


@app.post("/stt")
def stt():
    f = request.files.get("file")
    if f is None:
        return {"error": "no file"}, 400
    raw = f.read()
    try:
        wf = wave.open(io.BytesIO(raw), "rb")
        rec = KaldiRecognizer(model, wf.getframerate())
        rec.SetWords(True)
        while True:
            data = wf.readframes(4000)
            if len(data) == 0:
                break
            rec.AcceptWaveform(data)
        text = json.loads(rec.FinalResult()).get("text", "")
    except Exception:
        # Not a WAV container - try raw 16kHz mono fallback
        try:
            rec = KaldiRecognizer(model, 16000)
            rec.AcceptWaveform(raw)
            text = json.loads(rec.FinalResult()).get("text", "")
        except Exception:
            return {"error": "could not decode audio"}, 400
    return {"text": text}


@app.post("/tts")
def tts():
    body = request.get_json(force=True)
    text = (body.get("text") or "").strip()
    if not text:
        return {"error": "empty text"}, 400
    import subprocess
    proc = subprocess.run(piper_cmd, input=text.encode(), stdout=subprocess.PIPE, check=True)
    return send_file(io.BytesIO(proc.stdout), mimetype="audio/wav",
                     download_name="reply.wav")


@app.get("/health")
def health():
    return {"ok": True}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True, help="Path to Vosk model directory")
    parser.add_argument("--piper", default="piper", help="Path to piper executable")
    parser.add_argument("--voice", required=True, help="Path to Piper .onnx voice model")
    parser.add_argument("--port", type=int, default=5055)
    args = parser.parse_args()
    init(args)
    # Bind to localhost only - offline device, no external exposure
    app.run(host="127.0.0.1", port=args.port)
