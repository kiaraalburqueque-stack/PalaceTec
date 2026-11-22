import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { login } from '../services/adminService';
import { useAdmin } from '../context/AdminContext';

export default function LoginPage() {
  const navigate = useNavigate();
  const { reload } = useAdmin();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  async function submit(event) {
    event.preventDefault(); setError(''); setLoading(true);
    try { await login(email, password); await reload(); navigate('/'); }
    catch (err) { setError(err.message); }
    finally { setLoading(false); }
  }
  return <main className="login-screen"><form className="login-card" onSubmit={submit}>
    <div className="brand"><span>♛</span> PalaceTec <small>Administración</small></div>
    <h1>Iniciar sesión</h1>
    <p>Acceso exclusivo para administradores del casino.</p>
    <label htmlFor="admin-email">Correo</label>
    <input id="admin-email" type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required />
    <label htmlFor="admin-password">Contraseña</label>
    <input id="admin-password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
    {error && <p role="alert">{error}</p>}
    <button className="primary" disabled={loading}>{loading ? 'Ingresando...' : 'Ingresar'}</button>
  </form></main>;
}
