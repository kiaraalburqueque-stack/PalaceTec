import { useEffect, useState } from 'react';
import { getDailyLimit, saveDailyLimit } from '../services/responsibleService';
export default function DailyLimit() {
  const [summary, setSummary] = useState(null);
  const [amount, setAmount] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    let active = true;
    getDailyLimit().then((data) => { if (active) { setSummary(data); setAmount(data.limit ?? ''); } })
      .catch((err) => { if (active) setError(err.message); });
    return () => { active = false; };
  }, []);
  async function submit(event) {
    event.preventDefault(); setSaving(true); setError('');
    try { setSummary(await saveDailyLimit(amount)); }
    catch (err) { setError(err.message); }
    finally { setSaving(false); }
  }
  return <section className="bet-controls auth-form"><h2>Mis límites diarios</h2>
    <p>Define cuánto puedes apostar al día. Los premios no restauran el cupo. El día se calcula en Lima.</p>
    {summary ? <>
      <p>{summary.configured ? `Límite: ${summary.limit} CRD · Apostado: ${summary.spent} CRD · Disponible: ${summary.remaining} CRD` : 'Configura tu límite antes de apostar.'}</p>
      {summary.pending_limit != null && <p role="status">Aumento pendiente: {summary.pending_limit} CRD. Disponible desde {new Date(summary.effective_at).toLocaleString('es-PE', { timeZone: 'America/Lima' })} (Lima).</p>}
      <form onSubmit={submit}><label>Límite diario en CRD<input type="number" min="0" max="9999999999.99" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} required /></label>
        <p>Las reducciones son inmediatas; los aumentos esperan 24 horas. Cero bloquea nuevas apuestas.</p>
        <button disabled={saving}>{saving ? 'Guardando...' : 'Guardar límite'}</button>
      </form>
    </> : !error && <p role="status">Consultando límite...</p>}
    {error && <p role="alert">{error}</p>}
  </section>;
}
