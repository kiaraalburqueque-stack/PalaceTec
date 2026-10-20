import { useState } from 'react';
import { selfExclude } from '../services/responsibleService';
import { endSession } from '../services/api';
export default function SelfExclusion() {
  const [days, setDays] = useState('7');
  const [confirmed, setConfirmed] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  async function submit(event) {
    event.preventDefault(); if (!confirmed || saving) return;
    setSaving(true); setError('');
    try {
      const data = await selfExclude(days);
      sessionStorage.setItem('palacetec:notice', `Autoexclusión confirmada hasta ${new Date(data.self_excluded_until).toLocaleString('es-PE', { timeZone: 'America/Lima' })} (Lima).`);
      endSession();
    } catch (err) { setError(err.message); }
    finally { setSaving(false); }
  }
  return <section className="bet-controls auth-form"><h2>Autoexclusión</h2>
    <p>Bloquea el acceso y las apuestas durante el período elegido. No se puede cancelar anticipadamente; tus apuestas aceptadas se liquidarán.</p>
    <form onSubmit={submit}>
      <label>Duración<select value={days} onChange={(e) => { setDays(e.target.value); setConfirmed(false); }}>{[7, 30, 90].map((n) => <option key={n} value={n}>{n} días</option>)}</select></label>
      <label><input type="checkbox" checked={confirmed} onChange={(e) => setConfirmed(e.target.checked)} />Confirmo que se cerrará mi sesión y que no podré cancelar la autoexclusión.</label>
      <button disabled={!confirmed || saving}>{saving ? 'Confirmando...' : 'Confirmar autoexclusión'}</button>
    </form>{error && <p role="alert">{error}</p>}
  </section>;
}
