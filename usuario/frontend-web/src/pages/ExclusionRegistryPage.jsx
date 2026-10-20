import { useEffect, useState } from 'react';
import { apiRequest } from '../services/api';

export default function ExclusionRegistryPage() {
  const [allowed, setAllowed] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    let active = true;
    apiRequest('/api/v1/admin/status').then(() => { if (active) setAllowed(true); })
      .catch(() => { if (active) setError('Acceso reservado a administradores.'); });
    return () => { active = false; };
  }, []);
  async function submit(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const values = new FormData(form);
    setSaving(true); setError(''); setMessage('');
    try {
      const data = await apiRequest('/api/v1/responsible-gaming/excluded-people', {
        method: 'POST', body: JSON.stringify(Object.fromEntries(values)),
      });
      form.reset();
      setMessage(data.created ? 'Exclusión registrada.' : 'La exclusión ya estaba registrada.');
    } catch (err) { setError(err.message); }
    finally { setSaving(false); }
  }
  return <main className="main-sections"><section className="bet-controls auth-form">
    <h1>Registro de exclusiones</h1>
    {error && <p role="alert">{error}</p>}
    {!allowed && !error && <p role="status">Verificando permisos...</p>}
    {allowed && <form onSubmit={submit}>
      <p>Registro interno del proyecto. Usa únicamente información autorizada.</p>
      <label>DNI<input name="dni" inputMode="numeric" pattern="[0-9]{8}" maxLength={8} autoComplete="off" required /></label>
      <label>Fuente<input name="source" maxLength={120} required /></label>
      <label>Motivo<input name="reason" maxLength={255} /></label>
      <button disabled={saving}>{saving ? 'Registrando...' : 'Registrar exclusión'}</button>
    </form>}
    {message && <p role="status">{message}</p>}
  </section></main>;
}
