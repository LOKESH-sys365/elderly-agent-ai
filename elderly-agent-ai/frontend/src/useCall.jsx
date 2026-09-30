import { useEffect, useRef, useState, useCallback } from 'react';
import { getToken } from './api.js';

/** WebRTC over the app's /ws/signal socket. Senior = caller (offers), caregiver = callee (answers). */
export function useCall({ onRing, onEvent }) {
  const ws = useRef(null), pc = useRef(null), peer = useRef(null), local = useRef(null);
  const [remote, setRemote] = useState(null), [mine, setMine] = useState(null), [active, setActive] = useState(false);

  const send = (type, to, data = {}) => ws.current?.readyState === 1 && ws.current.send(JSON.stringify({ type, to, data }));

  const makePc = useCallback(async () => {
    pc.current = new RTCPeerConnection({ iceServers: [] }); // LAN only, fully offline
    local.current = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
    setMine(local.current);
    local.current.getTracks().forEach((t) => pc.current.addTrack(t, local.current));
    pc.current.ontrack = (e) => setRemote(e.streams[0]);
    pc.current.onicecandidate = (e) => e.candidate && send('ice', peer.current, e.candidate.toJSON());
    setActive(true);
  }, []);

  const hangup = useCallback((notify = true) => {
    if (notify && peer.current) send('hangup', peer.current);
    local.current?.getTracks().forEach((t) => t.stop());
    pc.current?.close(); pc.current = null; local.current = null; peer.current = null;
    setRemote(null); setMine(null); setActive(false);
  }, []);

  useEffect(() => {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    const s = new WebSocket(`${proto}://${location.host}/ws/signal?token=${encodeURIComponent(getToken())}`);
    ws.current = s;
    s.onmessage = async (ev) => {
      const { type, from, data } = JSON.parse(ev.data);
      if (type === 'ring') { peer.current = data.from; onRing?.(data); }
      else if (type === 'accept') {                    // callee picked up -> we send the offer
        peer.current = from; await makePc();
        const o = await pc.current.createOffer(); await pc.current.setLocalDescription(o);
        send('offer', from, o);
      } else if (type === 'offer') {
        await pc.current.setRemoteDescription(data);
        const a = await pc.current.createAnswer(); await pc.current.setLocalDescription(a);
        send('answer', from, a);
      } else if (type === 'answer') await pc.current?.setRemoteDescription(data);
      else if (type === 'ice') await pc.current?.addIceCandidate(data).catch(() => {});
      else if (type === 'hangup' || type === 'decline') hangup(false);
      onEvent?.(type, data);
    };
    return () => { s.close(); hangup(false); };
  }, []);

  const accept = async () => { await makePc(); send('accept', peer.current); };
  const decline = () => { send('decline', peer.current); peer.current = null; };
  return { remote, mine, active, accept, decline, hangup: () => hangup(true) };
}

export function Video({ stream, muted, className }) {
  const ref = useRef();
  useEffect(() => { if (ref.current) ref.current.srcObject = stream; }, [stream]);
  return <video ref={ref} autoPlay playsInline muted={muted} className={className} />;
}
