import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { resetPassword } from '../services/authService';
import { validatePassword } from '../utils/passwordValidation';

// HU55: define la contraseña nueva con el token recibido por correo.
export default function ResetPassword() {
  const { token } = useParams();
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [error, setError] = useState('');
  const [done, setDone] = useState('');
  const [loading, setLoading] = useState(false);
  const checks = validatePassword(password);
  async function submit(event) {
    event.preventDefault(); setError('');
    if (password !== confirmation) { setError('Las contraseñas no coinciden.'); return; }
    if (!Object.values(checks).every(Boolean)) { setError('Usa al menos ocho caracteres, una mayúscula y un símbolo.'); return; }
    setLoading(true);
    try { setDone((await resetPassword(token, password)).detail); }
    catch (err) { setError(err.message.replace(/^new_password: /, '')); }
    finally { setLoading(false); }
  }
  return <main className="main-sections"><section className="bet-controls auth-form"><h1>Nueva contraseña</h1>
    {done ? <><p role="status">{done}</p><Link className="place-bet-button link-as-button" to="/login">Iniciar sesión</Link></>
      : <form onSubmit={submit}>
        <p>Debe ser distinta de tu contraseña anterior. Al guardarla se cerrarán todas tus sesiones.</p>
        <label htmlFor="reset-password">Nueva contraseña</label>
        <input id="reset-password" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        <ul className="requirements">
          <li className={checks.minLength ? 'ok' : ''}>Mínimo 8 caracteres</li>
          <li className={checks.hasUppercase ? 'ok' : ''}>Al menos una letra mayúscula</li>
          <li className={checks.hasSymbol ? 'ok' : ''}>Al menos un símbolo especial</li>
        </ul>
        <label htmlFor="reset-confirmation">Confirmar contraseña</label>
        <input id="reset-confirmation" type="password" autoComplete="new-password" value={confirmation} onChange={(e) => setConfirmation(e.target.value)} required />
        {error && <p role="alert">{error}</p>}
        {error && /venció|no es válido|ya se usó/.test(error) && <Link to="/forgot-password">Solicitar un enlace nuevo</Link>}
        <button type="submit" className="place-bet-button" disabled={loading}>{loading ? 'Guardando...' : 'Guardar contraseña'}</button>
      </form>}
  </section></main>;
}
