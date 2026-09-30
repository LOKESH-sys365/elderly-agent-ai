let token = sessionStorage.getItem('token') || '';
export const setToken = (t) => { token = t; t ? sessionStorage.setItem('token', t) : sessionStorage.removeItem('token'); };
export const getToken = () => token;

async function req(path, opts = {}) {
  const r = await fetch('/api' + path, {
    ...opts,
    headers: { ...(opts.json ? { 'Content-Type': 'application/json' } : {}),
               ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: opts.json ? JSON.stringify(opts.json) : opts.body
  });
  if (r.status === 401 && token) { setToken(''); location.reload(); }
  if (!r.ok && r.status !== 429) throw new Error((await r.json().catch(() => ({}))).message || 'Error ' + r.status);
  if ((r.headers.get('content-type') || '').includes('json')) return r.json();
  return r;
}
export const api = {
  login: (username, password) => req('/auth/login', { method: 'POST', json: { username, password } }),
  register: (b) => req('/auth/register', { method: 'POST', json: b }),
  chat: (message) => req('/chat', { method: 'POST', json: { message } }),
  sos: (reason) => req('/emergency/sos', { method: 'POST', json: { reason } }),
  startCall: (message) => req('/calls/start', { method: 'POST', json: { message } }),
  reminders: () => req('/reminders'),
  today: () => req('/reminders/today'),
  addReminder: (b) => req('/reminders', { method: 'POST', json: b }),
  complete: (id) => req('/reminders/' + id + '/complete', { method: 'POST' }),
  remove: (id) => req('/reminders/' + id, { method: 'DELETE' }),
  talk: async (blob) => {
    const fd = new FormData(); fd.append('audio', blob, 'speech.webm'); fd.append('speak', 'true');
    const r = await req('/voice/talk', { method: 'POST', body: fd });
    return { transcript: decodeURIComponent(r.headers.get('X-Transcript') || ''), wav: await r.blob() };
  }
};
