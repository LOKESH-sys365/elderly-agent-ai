import React, { useState } from 'react';
import { api, setToken, getToken } from './api.js';
import Senior from './Senior.jsx';
import Caregiver from './Caregiver.jsx';

export default function App() {
  const [user, setUser] = useState(() => JSON.parse(sessionStorage.getItem('user') || 'null'));
  const logout = () => { setToken(''); sessionStorage.removeItem('user'); setUser(null); };
  if (user && getToken()) {
    return user.role === 'SENIOR' ? <Senior user={user} logout={logout} /> : <Caregiver user={user} logout={logout} />;
  }
  return <Login onDone={(u) => { sessionStorage.setItem('user', JSON.stringify(u)); setUser(u); }} />;
}

function Login({ onDone }) {
  const [f, setF] = useState({ username: '', password: '' }), [err, setErr] = useState('');
  const go = async (e) => {
    e.preventDefault(); setErr('');
    try { const r = await api.login(f.username, f.password); setToken(r.token); onDone({ role: r.role, name: r.displayName, username: f.username }); }
    catch (x) { setErr(x.message); }
  };
  return (
    <div className="login-wrap">
      <form className="login-card" onSubmit={go}>
        <h1>Maya</h1><p>Your friendly care assistant</p>
        <input placeholder="Username" autoComplete="username" value={f.username} onChange={(e) => setF({ ...f, username: e.target.value })} />
        <input placeholder="Password" type="password" autoComplete="current-password" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} />
        {err && <div className="err">{err}</div>}
        <button>Sign in</button>
      </form>
    </div>
  );
}
