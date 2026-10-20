import { useEffect, useState } from 'react';
import { getRiskAlerts, takePause } from '../services/responsibleService';
export default function RiskAlerts({ userId }) {
  const [data, setData] = useState(null);
  const [dismissed, setDismissed] = useState(() => JSON.parse(sessionStorage.getItem(`palacetec:risk:${userId}`) || '[]'));
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    let active = true;
    const sync = () => getRiskAlerts().then((value) => { if (active) { setData(value); setError(''); } })
      .catch((err) => { if (active) setError(err.message); });
    const receive = (event) => { if (event.detail?.userId === userId) void sync(); };
    void sync(); const poll = setInterval(sync, 15000); const tick = setInterval(() => setNow(Date.now()), 1000);
    window.addEventListener('focus', sync); window.addEventListener('palacetec:risk-alert', receive);
    window.addEventListener('palacetec:session-renewed', sync);
    return () => { active = false; clearInterval(poll); clearInterval(tick); window.removeEventListener('focus', sync); window.removeEventListener('palacetec:risk-alert', receive); window.removeEventListener('palacetec:session-renewed', sync); };
  }, [userId]);
  const incident = data?.results.find((item) => !item.pause_started_at && !dismissed.includes(item.incident_id));
  function dismiss() {
    const next = [...dismissed, incident.incident_id]; setDismissed(next);
    sessionStorage.setItem(`palacetec:risk:${userId}`, JSON.stringify(next));
  }
  const paused = Date.parse(data?.pause_until) > now;
  return <aside className="responsible-alerts" aria-label="Alertas de juego responsable">
    {paused && <p role="status">Pausa activa: quedan {Math.ceil((Date.parse(data.pause_until) - now) / 1000)} segundos. No puedes realizar nuevas apuestas.</p>}
    {incident && !paused && <section role="alert"><h2>Considera una pausa</h2><p>Se detectaron aumentos consecutivos después de pérdidas. Puedes descansar diez minutos.</p>
      <button disabled={saving} onClick={async () => {
        setSaving(true); setError('');
        try { const result = await takePause(incident.incident_id); setData((old) => ({ ...old, pause_until: result.pause_until })); window.dispatchEvent(new CustomEvent('palacetec:pause', { detail: { userId, ...result } })); dismiss(); }
        catch (err) { setError(err.message); } finally { setSaving(false); }
      }}>Tomar pausa de 10 minutos</button><button disabled={saving} onClick={dismiss}>Ahora no</button>
    </section>}
    {error && <p role="alert">No se pudieron consultar las alertas: {error}</p>}
  </aside>;
}
