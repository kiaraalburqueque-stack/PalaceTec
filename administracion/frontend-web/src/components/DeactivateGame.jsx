import { useState } from 'react';
import DialogModal from './DialogModal';
import { restoreGame, softDeleteGame } from '../services/adminService';

// HU45: desactivar (borrado lógico) con confirmación; también permite restaurar.
export default function DeactivateGame({ game, onChanged }) {
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  async function run(action, message) {
    setSaving(true); setError('');
    try { await action(game.id); setConfirming(false); onChanged(message); }
    catch (err) { setError(err.message); }
    finally { setSaving(false); }
  }
  if (game.deleted_at) return <button type="button" className="secondary small" disabled={saving}
    onClick={() => run(restoreGame, `${game.title} volvió a publicarse.`)}>Restaurar</button>;
  return <>
    <button type="button" className="danger small" onClick={() => setConfirming(true)}>Desactivar juego</button>
    {confirming && <DialogModal title="Desactivar juego" onClose={() => setConfirming(false)}>
      <p>¿Desactivar <strong>{game.title}</strong>? Dejará de verse en el catálogo y sus salas se cerrarán. Las rondas y apuestas pasadas se conservan.</p>
      {error && <p role="alert">{error}</p>}
      <div className="actions"><button type="button" className="secondary" onClick={() => setConfirming(false)}>Cancelar</button>
        <button type="button" className="danger" disabled={saving} onClick={() => run(softDeleteGame, `${game.title} fue desactivado. Su historial se conserva.`)}>{saving ? 'Desactivando...' : 'Sí, desactivar'}</button></div>
    </DialogModal>}
  </>;
}
