import { useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import useUser from '../hooks/useUser';
import { getUserProfile, updateProfile } from '../services/authService';

function ProfileForm({ profile, onSaved }) {
  const [values, setValues] = useState(() => ({ nickname: profile.nickname || '', avatar_url: profile.avatar_url || '/avatars/avatar-1.svg', first_name: profile.first_name || '', last_name: profile.last_name || '', phone: profile.phone || '' }));
  const [message, setMessage] = useState('');
  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);
  function change(event) { setValues((old) => ({ ...old, [event.target.name]: event.target.value })); }
  async function submit(event) {
    event.preventDefault(); setSaving(true); setMessage(''); setErrors({});
    const changes = Object.fromEntries(Object.entries(values).filter(([key, value]) => value !== (profile[key] || '')));
    try { const result = await updateProfile(changes); onSaved(result); setMessage('Perfil actualizado correctamente.'); }
    catch (err) { setMessage(err.message); setErrors(err.data || {}); }
    finally { setSaving(false); }
  }
  return <section className="bet-controls auth-form"><h1>Mi perfil</h1>
    <label>Correo<input value={profile.email} readOnly /></label><label>DNI<input value={profile.dni || ''} readOnly /></label>
    <form onSubmit={submit}>
      {[['nickname', 'Apodo'], ['first_name', 'Nombres'], ['last_name', 'Apellidos'], ['phone', 'Teléfono']].map(([name, title]) => <label key={name}>{title}<input name={name} value={values[name]} onChange={change} maxLength={name === 'nickname' ? 30 : name === 'phone' ? 20 : 80} minLength={name === 'nickname' && values[name] ? 3 : undefined} pattern={name === 'nickname' ? '[a-zA-Z0-9_]+' : undefined} type={name === 'phone' ? 'tel' : 'text'} aria-invalid={!!errors[name]} />{errors[name] && <small role="alert">{errors[name]}</small>}</label>)}
      <label>Avatar<select name="avatar_url" value={values.avatar_url} onChange={change}>{[1,2,3,4,5,6].map((i) => <option key={i} value={'/avatars/avatar-' + i + '.svg'}>Avatar {i}</option>)}</select></label>
      <img src={values.avatar_url} alt="Vista previa del avatar" width="80" height="80" />
      <button className="place-bet-button" disabled={saving}>{saving ? 'Guardando...' : 'Guardar perfil'}</button>
    </form>{message && <p role="status">{message}</p>}
  </section>;
}
export default function ProfilePage() {
  const { user, loading, setUser } = useUser();
  const [profile, setProfile] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    if (!user) return;
    let active = true;
    getUserProfile().then((data) => { if (active) setProfile(data); }).catch((err) => { if (active) setError(err.message); });
    return () => { active = false; };
  }, [user]);
  if (loading) return <p role="status">Consultando sesión...</p>;
  if (!user) return <Navigate to="/login" replace />;
  return <main className="main-sections">{error && <p role="alert">{error}</p>}{profile ? <ProfileForm profile={profile} onSaved={(result) => { setProfile(result); setUser({ ...user, ...result, name: result.nickname || result.email.split('@')[0], nickname: result.nickname || result.email.split('@')[0] }); }} /> : !error && <p role="status">Consultando perfil...</p>}</main>;
}
