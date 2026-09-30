import React, { useEffect, useState } from 'react';
import { api } from './api.js';
import { useCall, Video } from './useCall.jsx';

const Ecg = () => (
  <svg className="ecg" viewBox="0 0 300 40" preserveAspectRatio="none">
    <polyline fill="none" strokeWidth="2" points="0,20 50,20 60,20 66,6 72,34 78,20 130,20 140,20 146,8 152,32 158,20 210,20 220,20 226,6 232,34 238,20 300,20" />
  </svg>);

export default function Caregiver({ user, logout }) {
  const [rem, setRem] = useState([]), [alerts, setAlerts] = useState([]), [ring, setRing] = useState(null);
  const [f, setF] = useState({ title: '', remindAt: '', type: 'MEDICATION', recurringDaily: true, notes: '' });
  const call = useCall({
    onRing: (d) => setRing(d),
    onEvent: (t, d) => { if (/sos|emergency/i.test(t)) setAlerts((a) => [{ at: new Date(), text: d?.reason || d?.message || 'Emergency alert', who: d?.fromName || d?.user || 'Senior' }, ...a]); if (t === 'hangup') setRing(null); }
  });
  const load = () => api.reminders().then(setRem).catch(() => {});
  useEffect(() => { load(); }, []);

  const add = async (e) => {
    e.preventDefault();
    await api.addReminder({ ...f, remindAt: f.remindAt + ':00' });
    setF({ ...f, title: '', notes: '' }); load();
  };
  const callSenior = async () => { const r = await api.startCall(''); alert(r.message); };

  if (call.active) return (
    <div className="c-app"><Header user={user} logout={logout} />
      <main className="c-video"><Video stream={call.remote} className="c-remote" /><Video stream={call.mine} muted className="c-self" />
        <button className="c-btn danger" onClick={call.hangup}>End call</button></main></div>);

  const pending = rem.filter((r) => !r.completed).length;
  return (
    <div className="c-app">
      <Header user={user} logout={logout} />
      {ring && <div className="c-ring">
        <b>📞 Incoming call from {ring.fromName}</b>{ring.message && <span> – “{ring.message}”</span>}
        <div><button className="c-btn" onClick={() => { call.accept(); setRing(null); }}>Answer</button>
          <button className="c-btn ghost" onClick={() => { call.decline(); setRing(null); }}>Decline</button></div></div>}
      {alerts.length > 0 && <div className="c-alert">⚠ <b>Emergency:</b> {alerts[0].who} – {alerts[0].text} ({alerts[0].at.toLocaleTimeString()})</div>}
      <main className="c-grid">
        <section className="card stat"><h3>Care status</h3><div className="big ok">● Stable</div><Ecg /><small>Live connection to the home assistant</small></section>
        <section className="card stat"><h3>Open reminders</h3><div className="big">{pending}</div><small>{rem.length - pending} completed</small></section>
        <section className="card stat"><h3>Video visit</h3><button className="c-btn" onClick={callSenior}>📹 Call senior</button><small>Peer-to-peer on your home network</small></section>

        <section className="card wide"><h3>Medication & appointment schedule</h3>
          <table><thead><tr><th>Time</th><th>Type</th><th>Item</th><th>Repeat</th><th>Status</th><th></th></tr></thead>
            <tbody>{rem.map((r) => (
              <tr key={r.id}><td>{new Date(r.remindAt).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })}</td>
                <td><span className={'pill ' + r.type}>{r.type}</span></td><td>{r.title}</td><td>{r.recurringDaily ? 'Daily' : '—'}</td>
                <td>{r.completed ? <span className="pill OK">Taken</span> : <span className="pill WAIT">Pending</span>}</td>
                <td><button className="link" onClick={() => api.remove(r.id).then(load)}>Remove</button></td></tr>))}
              {rem.length === 0 && <tr><td colSpan="6" className="empty">No reminders yet.</td></tr>}</tbody></table>
        </section>

        <section className="card"><h3>Add reminder</h3>
          <form className="c-form" onSubmit={add}>
            <label>Title<input required value={f.title} onChange={(e) => setF({ ...f, title: e.target.value })} placeholder="Blood pressure tablet" /></label>
            <label>When<input required type="datetime-local" value={f.remindAt} onChange={(e) => setF({ ...f, remindAt: e.target.value })} /></label>
            <label>Type<select value={f.type} onChange={(e) => setF({ ...f, type: e.target.value })}><option>MEDICATION</option><option>APPOINTMENT</option><option>GENERAL</option></select></label>
            <label className="chk"><input type="checkbox" checked={f.recurringDaily} onChange={(e) => setF({ ...f, recurringDaily: e.target.checked })} /> Repeat daily</label>
            <button className="c-btn">Save reminder</button>
          </form></section>
      </main>
    </div>);
}

const Header = ({ user, logout }) => (
  <header className="c-head"><div className="logo">✚ <b>Maya</b> Care</div>
    <div>{user.name} · Caregiver <button className="link" onClick={logout}>Sign out</button></div></header>);
