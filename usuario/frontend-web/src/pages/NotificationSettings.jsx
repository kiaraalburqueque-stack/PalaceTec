import { useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import useUser from '../hooks/useUser';
import { getNotificationPreferences, updateNotificationPreferences } from '../services/notificationService';

// HU58: interruptores de promociones, seguridad y alertas de juego.
const OPTIONS = [
  { key: 'promotions', title: 'Promociones', help: 'Bonos, torneos y novedades del casino.' },
  { key: 'security', title: 'Seguridad', help: 'Nuevos accesos a tu cuenta y cambios importantes. Recomendamos mantenerla activa.' },
  { key: 'game_alerts', title: 'Alertas de juego', help: 'Resultados y premios de tus rondas.' },
];

export default function NotificationSettings() {
  const { user, loading } = useUser();
  const [prefs, setPrefs] = useState(null);
  const [saving, setSaving] = useState(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  useEffect(() => {
    if (!user) return;
    let active = true;
    getNotificationPreferences().then((data) => { if (active) setPrefs(data); }).catch((err) => { if (active) setError(err.message); });
    return () => { active = false; };
  }, [user]);
  if (loading) return <p role="status">Consultando sesión...</p>;
  if (!user) return <Navigate to="/login" replace />;
  async function toggle(key) {
    const value = !prefs[key];
    setSaving(key); setError(''); setMessage('');
    setPrefs((old) => ({ ...old, [key]: value }));
    try { setPrefs(await updateNotificationPreferences({ [key]: value })); setMessage('Preferencias guardadas.'); }
    catch (err) { setPrefs((old) => ({ ...old, [key]: !value })); setError(err.message); }
    finally { setSaving(null); }
  }
  return <main className="main-sections"><section className="bet-controls auth-form notification-settings">
    <h1>Preferencias de notificación</h1>
    <p>Elige qué avisos quieres recibir en la campana y por correo.</p>
    {!prefs && !error && <p role="status">Cargando preferencias...</p>}
    {prefs && <ul className="switch-list">{OPTIONS.map((option) => <li key={option.key}>
      <div><strong id={`pref-${option.key}`}>{option.title}</strong><small>{option.help}</small></div>
      <button type="button" role="switch" aria-checked={prefs[option.key]} aria-labelledby={`pref-${option.key}`}
        className="switch" disabled={saving === option.key} onClick={() => toggle(option.key)}><span /></button>
    </li>)}</ul>}
    {message && <p role="status">{message}</p>}
    {error && <p role="alert">{error}</p>}
  </section></main>;
}
