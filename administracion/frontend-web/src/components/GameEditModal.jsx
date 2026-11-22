import { useState } from 'react';
import DialogModal from './DialogModal';
import { updateGame } from '../services/adminService';
import { serverErrors, validateGame } from '../utils/games';

// HU44: edición de apuestas mínima y máxima y RTP con los datos precargados.
export default function GameEditModal({ game, onClose, onSaved }) {
  const [values, setValues] = useState({ title: game.title, min_bet: game.min_bet, max_bet: game.max_bet, rtp: game.rtp });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const set = (key, value) => setValues((old) => ({ ...old, [key]: value }));
  async function submit(event) {
    event.preventDefault(); setError('');
    const found = validateGame(values);
    setErrors(found);
    if (Object.keys(found).length) return;
    setSaving(true);
    try { onSaved(await updateGame(game.id, values)); }
    catch (err) { setErrors(serverErrors(err.data)); setError(err.message); setSaving(false); }
  }
  const input = (key, label, props = {}) => <div><label htmlFor={`edit-${key}`}>{label}</label>
    <input id={`edit-${key}`} value={values[key]} onChange={(e) => set(key, e.target.value)} aria-invalid={Boolean(errors[key])} {...props} />
    {errors[key] && <small className="field-error">{errors[key]}</small>}</div>;
  return <DialogModal title={`Editar ${game.title}`} onClose={onClose}>
    <form className="form-grid two-columns" onSubmit={submit} noValidate>
      <div className="full">{input('title', 'Título')}</div>
      {input('min_bet', 'Apuesta mínima (CRD)', { type: 'number', step: '0.01', min: '1' })}
      {input('max_bet', 'Apuesta máxima (CRD)', { type: 'number', step: '0.01', min: '1' })}
      {input('rtp', 'RTP (%)', { type: 'number', step: '0.01', min: '80', max: '99.99' })}
      <p className="muted full">Las salas abiertas de este juego reciben los nuevos límites al instante y el cambio queda en la auditoría.</p>
      {error && <p role="alert" className="full">{error}</p>}
      <div className="actions full"><button type="button" className="secondary" onClick={onClose}>Cancelar</button><button className="primary" disabled={saving}>{saving ? 'Guardando...' : 'Guardar cambios'}</button></div>
    </form>
  </DialogModal>;
}
