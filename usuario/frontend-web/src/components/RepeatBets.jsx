import { useEffect, useState } from 'react';
import { getPreviousBets, repeatBets } from '../services/roomService';
export default function RepeatBets({ roomId, roundId, open, balance, remaining, onRepeated }) {
  const [previous, setPrevious] = useState(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [pending, setPending] = useState(null);
  useEffect(() => {
    let active = true;
    getPreviousBets(roomId).then((data) => { if (active) setPrevious(data); })
      .catch((err) => { if (active) setError(err.message); });
    return () => { active = false; };
  }, [roomId, roundId]);
  const total = previous?.bets.reduce((sum, bet) => sum + Math.round(Number(bet.amount) * 100), 0) / 100 || 0;
  async function repeat() {
    if (saving) return;
    setSaving(true); setError(''); setMessage('');
    const payload = pending || { round_id: roundId, source_round_id: previous.source_round_id, reference: crypto.randomUUID() };
    setPending(payload);
    try {
      await repeatBets(roomId, payload); setPending(null);
      setMessage('Conjunto anterior repetido correctamente.');
      await onRepeated?.();
    } catch (err) { if (err.status && err.status < 500) setPending(null); setError(err.message); }
    finally { setSaving(false); }
  }
  return <section className="prebet-balance"><h2>Repetir apuestas</h2>
    {previous?.bets.length ? <><p>Ronda anterior {previous.source_round_id}: {previous.bets.length} apuestas · Total {total.toFixed(2)} CRD</p>
      <button disabled={saving || (!pending && (!open || !roundId || total > balance || total > Number(remaining)))} onClick={repeat}>{saving ? 'Repitiendo...' : pending ? 'Reintentar repetición pendiente' : 'Repetir conjunto anterior'}</button>
      <p>Se repite todo el conjunto o no se registra ninguna apuesta.</p>
    </> : <p>No tienes una ronda anterior liquidada para repetir en esta sala.</p>}
    {error && <p role="alert">{error}</p>}{message && <p role="status">{message}</p>}
  </section>;
}
