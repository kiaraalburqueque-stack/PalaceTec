import { useState } from 'react';
import { sendPromotion } from '../services/adminService';

// Envía una promoción a la campana de los jugadores; respeta sus preferencias (HU58).
export default function Promotions() {
  const [values, setValues] = useState({ title: '', message: '', send_email: false });
  const [result, setResult] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  async function submit(event) {
    event.preventDefault(); setSaving(true); setError(''); setResult('');
    try { const data = await sendPromotion(values); setResult(`Enviada a ${data.sent} jugador(es). ${data.skipped} la tienen desactivada.`); setValues({ title: '', message: '', send_email: false }); }
    catch (err) { setError(err.message); }
    finally { setSaving(false); }
  }
  return <section className="page">
    <header className="page-header"><div><h1>Promociones</h1><p>Aviso para todos los jugadores activos que aceptan promociones.</p></div></header>
    <form className="card form-grid narrow" onSubmit={submit}>
      <label htmlFor="promo-title">Título</label>
      <input id="promo-title" value={values.title} minLength={3} maxLength={120} onChange={(e) => setValues({ ...values, title: e.target.value })} required />
      <label htmlFor="promo-message">Mensaje</label>
      <textarea id="promo-message" rows={4} value={values.message} minLength={5} maxLength={500} onChange={(e) => setValues({ ...values, message: e.target.value })} required />
      <label className="checkbox"><input type="checkbox" checked={values.send_email} onChange={(e) => setValues({ ...values, send_email: e.target.checked })} /> Enviar también por correo</label>
      {result && <p className="notice" role="status">{result}</p>}
      {error && <p role="alert">{error}</p>}
      <div className="actions"><button className="primary" disabled={saving}>{saving ? 'Enviando...' : 'Enviar promoción'}</button></div>
    </form>
  </section>;
}
