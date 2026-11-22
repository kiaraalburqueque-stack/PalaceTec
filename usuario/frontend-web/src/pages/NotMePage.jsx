import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { reportNotMe } from '../services/authService';
import { endSession } from '../services/api';

// HU56: destino del botón "No fui yo" del correo de alerta de acceso.
export default function NotMePage() {
  const { token = '' } = useParams();
  const [result, setResult] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  async function confirm() {
    setLoading(true); setError('');
    try { setResult((await reportNotMe(token)).detail); endSession(); }
    catch (err) { setError(err.message); }
    finally { setLoading(false); }
  }
  return <main className="main-sections"><section className="bet-controls auth-form security-alert">
    <h1>¿No reconoces este acceso?</h1>
    {result ? <><p role="status">{result}</p><Link className="place-bet-button link-as-button" to="/forgot-password">Restablecer contraseña</Link></>
      : <><p>Al confirmar, bloquearemos tu cuenta durante 24 horas y cerraremos todas las sesiones abiertas, incluida la de quien entró.</p>
        <p>Luego podrás recuperar el acceso restableciendo tu contraseña.</p>
        {!token && <p role="alert">El enlace está incompleto. Ábrelo nuevamente desde el correo.</p>}
        {error && <p role="alert">{error}</p>}
        <button type="button" className="place-bet-button danger-button" disabled={!token || loading} onClick={confirm}>{loading ? 'Bloqueando...' : 'No fui yo: bloquear mi cuenta'}</button>
        <Link to="/">Fui yo, volver al casino</Link></>}
  </section></main>;
}
