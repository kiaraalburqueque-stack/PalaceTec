import { useState } from 'react';
import { Navigate } from 'react-router-dom';
import useUser from '../hooks/useUser';
import { updateProfile } from '../services/authService';

export default function ProfilePage() {
  const { user, loading, setUser } = useUser();
  const [nickname, setNickname] = useState('');
  const [avatar, setAvatar] = useState('/avatars/avatar-1.svg');
  const [message, setMessage] = useState('');
  const [saving, setSaving] = useState(false);
  if (loading) return <p role="status">Consultando sesión...</p>;
  if (!user) return <Navigate to="/login" replace />;
  async function submit(event) {
    event.preventDefault(); setSaving(true); setMessage('');
    try {
      const result = await updateProfile({ nickname, avatar_url: avatar });
      setUser({ ...result, name: result.nickname, nickname: result.nickname });
      setMessage('Perfil actualizado correctamente.');
    } catch (err) { setMessage(err.message); }
    finally { setSaving(false); }
  }
  return <main className="main-sections"><section className="bet-controls auth-form"><h1>Mi perfil</h1>
    <p>Correo: {user.email}</p><p>Apodo actual: {user.nickname}</p>
    <form onSubmit={submit}><label htmlFor="nickname">Nuevo apodo</label><input id="nickname" value={nickname} onChange={(e) => setNickname(e.target.value)} minLength={3} maxLength={30} pattern="[a-zA-Z0-9_]+" required />
      <label htmlFor="avatar">Avatar</label><select id="avatar" value={avatar} onChange={(e) => setAvatar(e.target.value)}>{[1,2,3,4,5,6].map((i) => <option key={i} value={'/avatars/avatar-' + i + '.svg'}>Avatar {i}</option>)}</select>
      <img src={avatar} alt="Vista previa del avatar" width="80" height="80" />
      <button className="place-bet-button" disabled={saving}>{saving ? 'Guardando...' : 'Guardar perfil'}</button>
    </form>{message && <p role="status">{message}</p>}
  </section></main>;
}
