import React, { useEffect, useRef, useState } from 'react';
import { api } from './api.js';
import { useCall, Video } from './useCall.jsx';

const clock = () => new Date().toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });

export default function Senior({ user, logout }) {
  const [time, setTime] = useState(clock()), [reminders, setReminders] = useState([]);
  const [log, setLog] = useState([{ who: 'Maya', text: `Hello ${user.name}! Press the big green button and tell me what you need.` }]);
  const [rec, setRec] = useState(false), [busy, setBusy] = useState(false), [text, setText] = useState('');
  const [banner, setBanner] = useState(''); const mr = useRef(), chunks = useRef([]);
  const call = useCall({ onEvent: (t) => t === 'decline' && setBanner('Your family could not answer right now.') });

  const load = () => api.today().then(setReminders).catch(() => {});
  useEffect(() => { load(); const i = setInterval(() => setTime(clock()), 15000); return () => clearInterval(i); }, []);
  const say = (who, t) => setLog((l) => [...l.slice(-20), { who, text: t }]);

  const ask = async (msg) => {
    say('You', msg); setBusy(true);
    try { const r = await api.chat(msg); say('Maya', r.reply); speak(r.reply); load(); }
    catch { say('Maya', 'Sorry, I had trouble. Please try again.'); }
    setBusy(false);
  };
  const speak = (t) => { try { speechSynthesis.cancel(); speechSynthesis.speak(new SpeechSynthesisUtterance(t)); } catch {} };

  const holdStart = async () => {
    try {
      const s = await navigator.mediaDevices.getUserMedia({ audio: true });
      mr.current = new MediaRecorder(s); chunks.current = [];
      mr.current.ondataavailable = (e) => chunks.current.push(e.data);
      mr.current.onstop = async () => {
        s.getTracks().forEach((t) => t.stop()); setBusy(true);
        try {
          const { transcript, wav } = await api.talk(new Blob(chunks.current, { type: 'audio/webm' }));
          if (transcript) say('You', transcript);
          new Audio(URL.createObjectURL(wav)).play(); say('Maya', '🔊 (speaking…)'); load();
        } catch { say('Maya', 'I could not hear you. Please try again.'); }
        setBusy(false);
      };
      mr.current.start(); setRec(true);
    } catch { say('Maya', 'Please allow the microphone.'); }
  };
  const holdEnd = () => { if (mr.current?.state === 'recording') mr.current.stop(); setRec(false); };

  const sos = async () => {
    if (!confirm('Call for HELP now?')) return;
    const r = await api.sos('SOS button pressed'); setBanner(r.message || 'Help is being alerted.');
  };
  const family = async () => { setBanner('Calling your family…'); const r = await api.startCall(''); setBanner(r.message); };
  const done = async (id) => { await api.complete(id); load(); };

  if (call.active) return (
    <div className="s-desktop"><div className="win">
      <div className="titlebar"><span>📞 Family Call</span></div>
      <div className="win-body">
        <Video stream={call.remote} className="s-video" />
        <Video stream={call.mine} muted className="s-self" />
        <button className="s-btn red" onClick={call.hangup}>HANG UP</button>
      </div></div></div>
  );

  return (
    <div className="s-desktop">
      <div className="win main">
        <div className="titlebar"><span>🌻 Maya – Helper</span><span>{time}</span></div>
        <div className="marquee"><span>★ Welcome, {user.name}! ★ Remember to drink water ★ You are doing great ★</span></div>
        {banner && <div className="s-banner" onClick={() => setBanner('')}>{banner} (tap to close)</div>}
        <div className="win-body">
          <div className="s-grid">
            <button className={'s-btn green ' + (rec ? 'rec' : '')} onMouseDown={holdStart} onMouseUp={holdEnd}
              onTouchStart={holdStart} onTouchEnd={holdEnd} disabled={busy}>
              🎤 {rec ? 'LISTENING… LET GO WHEN DONE' : 'HOLD & TALK'}
            </button>
            <button className="s-btn blue" onClick={family}>📞 CALL FAMILY</button>
            <button className="s-btn yellow" onClick={() => ask('What are my reminders for today?')}>💊 MY MEDICINES</button>
            <button className="s-btn red" onClick={sos}>🚨 HELP! (SOS)</button>
          </div>
          <fieldset><legend>Today's list</legend>
            {reminders.length === 0 && <p>Nothing to do today. 😊</p>}
            {reminders.map((r) => (
              <div key={r.id} className={'s-rem ' + (r.completed ? 'done' : '')}>
                <span>{r.type === 'MEDICATION' ? '💊' : '📌'} {new Date(r.remindAt).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })} – {r.title}</span>
                {!r.completed && <button onClick={() => done(r.id)}>Done ✔</button>}
              </div>))}
          </fieldset>
          <fieldset><legend>Chat with Maya</legend>
            <div className="s-log">{log.map((m, i) => <p key={i}><b>{m.who}:</b> {m.text}</p>)}{busy && <p><i>Maya is thinking…</i></p>}</div>
            <form onSubmit={(e) => { e.preventDefault(); if (text.trim()) { ask(text.trim()); setText(''); } }}>
              <input value={text} onChange={(e) => setText(e.target.value)} placeholder="Or type here…" />
              <button>Send</button>
            </form>
          </fieldset>
        </div>
        <div className="statusbar"><span>Logged in: {user.name}</span><button onClick={logout}>Log off</button></div>
      </div>
    </div>
  );
}
