import { useState } from 'react';
import { Navigate } from 'react-router-dom';
import useUser from '../hooks/useUser';
import { changePassword } from '../services/authService';
import { endSession } from '../services/api';
import { validatePassword } from '../utils/passwordValidation';
export default function PasswordPage() {
  const { user, loading } = useUser();
  const [current, setCurrent] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  if (loading) return <p role="status">Consultando sesión...</p>;
  if (!user) return <Navigate to="/login" replace />;
  async function submit(event) {
    event.preventDefault(); setError('');
    if (password !== confirmation) { setError('Las contraseñas no coinciden.'); return; }
    if (current === password || !Object.values(validatePassword(password)).every(Boolean)) { setError('Usa una contraseña diferente con al menos ocho caracteres, una mayúscula y un símbolo.'); return; }
    setSaving(true);
    try {
      await changePassword({ current_password: current, new_password: password });
      setCurrent(''); setPassword(''); setConfirmation('');
      sessionStorage.setItem('palacetec:notice', 'Contraseña actualizada. Inicia sesión nuevamente.');
      endSession();
    } catch (err) { setError(err.message); }
    finally { setSaving(false); }
  }
  return <main className="main-sections"><section className="bet-controls auth-form"><h1>Cambiar contraseña</h1>
    <p>Al guardar se cerrarán todas tus sesiones.</p>
    <form onSubmit={submit}>
      <label>Contraseña actual<input type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} required /></label>
      <label>Nueva contraseña<input type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} minLength={8} required /></label>
      <small>Mínimo ocho caracteres, una mayúscula y un símbolo.</small>
      <label>Confirmar contraseña<input type="password" autoComplete="new-password" value={confirmation} onChange={(e) => setConfirmation(e.target.value)} required /></label>
      <button disabled={saving}>{saving ? 'Guardando...' : 'Cambiar contraseña'}</button>
    </form>{error && <p role="alert">{error}</p>}
  </section></main>;
}
