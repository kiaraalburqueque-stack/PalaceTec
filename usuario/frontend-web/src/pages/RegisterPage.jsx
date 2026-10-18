import { useState } from 'react';
import { Link } from 'react-router-dom';
import { register } from '../services/authService';
import PasswordStrength from '../components/PasswordStrength';
import { validatePassword } from '../utils/passwordValidation';

export default function RegisterPage() {
  const [data, setData] = useState({ email: '', dni: '', birth_date: '', password: '' });
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);
  const change = (event) => setData({ ...data, [event.target.name]: event.target.value });
  async function submit(event) {
    event.preventDefault();
    if (loading) return;
    setError('');
    if (!Object.values(validatePassword(data.password)).every(Boolean)) {
      setError('La contraseña debe tener al menos 8 caracteres, una mayúscula y un símbolo.');
      return;
    }
    setLoading(true);
    try { await register(data); setSuccess(true); }
    catch (err) { setError(err.message); }
    finally { setLoading(false); }
  }
  return <main className="main-sections"><section className="bet-controls auth-form">
    <h1>Crear cuenta en PalaceTec</h1>
    {success ? <div role="status"><h2>¡Cuenta creada!</h2><p>Recibiste 1,000 créditos de bienvenida. Inicia sesión para jugar.</p><Link to="/login">Iniciar sesión</Link></div> : <>
      <p>Regístrate para recibir 1,000 créditos virtuales. Solo mayores de 18 años.</p>
      <form onSubmit={submit}>
        <label htmlFor="register-email">Correo electrónico</label><input id="register-email" name="email" type="email" autoComplete="email" value={data.email} onChange={change} required />
        <label htmlFor="register-dni">DNI</label><input id="register-dni" name="dni" inputMode="numeric" pattern="[0-9]{8}" maxLength={8} value={data.dni} onChange={change} required />
        <label htmlFor="register-birth">Fecha de nacimiento</label><input id="register-birth" name="birth_date" type="date" value={data.birth_date} onChange={change} required />
        <PasswordStrength value={data.password} onChange={(password) => setData({ ...data, password })} embedded />
        {error && <p role="alert">{error}</p>}
        <button className="place-bet-button" type="submit" disabled={loading}>{loading ? 'Creando cuenta...' : 'Crear cuenta'}</button>
      </form><p>¿Ya tienes cuenta? <Link to="/login">Iniciar sesión</Link></p>
    </>}
  </section></main>;
}
