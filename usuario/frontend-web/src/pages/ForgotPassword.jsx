import { useState } from 'react';
import { Link } from 'react-router-dom';
import { forgotPassword } from '../services/authService';

// HU55: solicitud del enlace para restablecer la contraseña.
export default function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  async function submit(event) {
    event.preventDefault(); setError(''); setLoading(true);
    try { setSent((await forgotPassword(email)).detail); }
    catch (err) { setError(err.message); }
    finally { setLoading(false); }
  }
  return <main className="main-sections"><section className="bet-controls auth-form"><h1>¿Olvidaste tu contraseña?</h1>
    {sent ? <><p role="status">{sent}</p><p>El enlace vence en 15 minutos y solo puede usarse una vez. Revisa también tu carpeta de spam.</p>
      <Link to="/login">Volver a iniciar sesión</Link></>
      : <><p>Escribe el correo de tu cuenta y te enviaremos un enlace para crear una contraseña nueva.</p>
        <form onSubmit={submit}>
          <label htmlFor="forgot-email">Correo electrónico</label>
          <input id="forgot-email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          {error && <p role="alert">{error}</p>}
          <button type="submit" className="place-bet-button" disabled={loading}>{loading ? 'Enviando...' : 'Enviar enlace'}</button>
        </form><Link to="/login">Volver a iniciar sesión</Link></>}
  </section></main>;
}
