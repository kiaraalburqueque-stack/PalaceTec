import { useEffect, useState } from 'react';
import DialogModal from './DialogModal';
import { getSanctions, setUserStatus } from '../services/adminService';
import { dateTime } from '../utils/format';

// HU42: confirmación con motivo obligatorio para suspender o activar una cuenta.
export default function SanctionModal({ user, onClose, onDone }) {
  const suspending = user.status === 'ACTIVE';
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [history, setHistory] = useState([]);
  useEffect(() => { getSanctions(user.id).then(setHistory).catch(() => setHistory([])); }, [user.id]);
  async function submit(event) {
    event.preventDefault();
    if (reason.trim().length < 5) { setError('Escribe el motivo de la sanción (mínimo 5 caracteres).'); return; }
    setSaving(true); setError('');
    try { await setUserStatus(user.id, !suspending, reason.trim()); onDone(); }
    catch (err) { setError(err.message.replace(/^reason: /, '')); setSaving(false); }
  }
  return <DialogModal title={suspending ? 'Suspender cuenta' : 'Activar cuenta'} onClose={onClose}>
    <form onSubmit={submit} className="form-grid">
      <p>{suspending ? <>Vas a suspender a <strong>{user.email}</strong>. Se cerrarán sus sesiones y será desconectado de inmediato; no podrá iniciar sesión.</>
        : <>Vas a reactivar a <strong>{user.email}</strong>. Podrá volver a iniciar sesión.</>}</p>
      <label htmlFor="sanction-reason">Motivo de la sanción *</label>
      <textarea id="sanction-reason" rows={3} maxLength={255} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="Ej.: uso de varias cuentas" aria-invalid={Boolean(error)} />
      {error && <p role="alert">{error}</p>}
      {history.length > 0 && <details><summary>Historial de sanciones ({history.length})</summary><ul className="history-list">
        {history.map((item) => <li key={item.id}><strong>{item.action === 'SUSPEND' ? 'Suspensión' : 'Activación'}</strong> · {dateTime(item.created_at)} · {item.admin}<br />{item.reason}</li>)}</ul></details>}
      <div className="actions"><button type="button" className="secondary" onClick={onClose}>Cancelar</button>
        <button className={suspending ? 'danger' : 'primary'} disabled={saving}>{saving ? 'Guardando...' : suspending ? 'Confirmar suspensión' : 'Confirmar activación'}</button></div>
    </form>
  </DialogModal>;
}
