import { useEffect, useState } from 'react';
import { getSessionSummary } from '../services/responsibleService';
import { logout } from '../services/authService';
import Modal from './Modal';
export default function RealityCheck() {
  const [summary, setSummary] = useState(null);
  const [now, setNow] = useState(Date.now);
  const [next, setNext] = useState(0);
  const [error, setError] = useState('');
  const [leaving, setLeaving] = useState(false);
  useEffect(() => {
    let active = true;
    const sync = () => getSessionSummary().then((data) => {
      if (active) { setSummary({ ...data, receivedAt: Date.now() }); setError(''); }
    }).catch((err) => { if (active) setError(err.message); });
    void sync();
    const poll = setInterval(sync, 30000);
    const tick = setInterval(() => setNow(Date.now()), 1000);
    window.addEventListener('focus', sync);
    return () => { active = false; clearInterval(poll); clearInterval(tick); window.removeEventListener('focus', sync); };
  }, []);
  if (!summary) return error ? <p role="alert">No se pudo consultar el tiempo de sesión: {error}</p> : null;
  const duration = summary.duration_seconds + Math.max(0, Math.floor((now - summary.receivedAt) / 1000));
  const interval = summary.reality_check_interval_seconds;
  const storageKey = `palacetec:reality:${summary.session_started_at}`;
  const threshold = Math.max(interval, Number(sessionStorage.getItem(storageKey)) || 0, next);
  if (duration < threshold) return null;
  function proceed() {
    const value = (Math.floor(duration / interval) + 1) * interval;
    sessionStorage.setItem(storageKey, String(value)); setNext(value);
  }
  return <Modal title="Tiempo de hacer una pausa">
    <p>Llevas {Math.floor(duration / 60)} minutos en esta sesión.</p>
    <p>Apuestas liquidadas: {summary.total_wagered} CRD · Pago total: {summary.total_paid} CRD · Neto: {summary.net} CRD</p>
    <p>Pendiente: {summary.pending_wagered} CRD. La ronda continúa mientras decides.</p>
    <button onClick={proceed} disabled={leaving}>Continuar jugando</button>
    <button disabled={leaving} onClick={async () => { setLeaving(true); try { await logout(); } catch (err) { setError(err.message); } finally { setLeaving(false); } }}>Cerrar sesión</button>
    {error && <p role="alert">{error}</p>}
  </Modal>;
}
