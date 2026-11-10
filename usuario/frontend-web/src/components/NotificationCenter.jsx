import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { connectNotifications, getNotifications, markAllNotificationsRead, markNotificationRead } from '../services/notificationService';

const ICONS = { PROMOTION: '🎁', SECURITY: '🔒', GAME: '🎰' };

function timeAgo(iso) {
  const seconds = Math.max(0, Math.round((Date.now() - Date.parse(iso)) / 1000));
  if (seconds < 60) return 'hace un momento';
  if (seconds < 3600) return `hace ${Math.floor(seconds / 60)} min`;
  if (seconds < 86400) return `hace ${Math.floor(seconds / 3600)} h`;
  return new Date(iso).toLocaleDateString('es-PE', { timeZone: 'America/Lima', day: '2-digit', month: 'short' });
}

// HU57: campana con contador de no leídas y lista desplegable en el Header.
export default function NotificationCenter({ userId, live = true }) {
  const [items, setItems] = useState([]);
  const [unread, setUnread] = useState(0);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState('');
  const ref = useRef(null);
  useEffect(() => {
    let active = true;
    getNotifications().then((data) => { if (active) { setItems(data.results); setUnread(data.unread_count); } })
      .catch((err) => { if (active) setError(err.message); });
    const stop = live ? connectNotifications(({ event, data }) => {
      if (!active) return;
      if (event === 'notification:new') {
        setItems((old) => [data, ...old.filter((item) => item.id !== data.id)].slice(0, 30));
        setUnread(data.unread_count);
      }
      if (event === 'notification:count') setUnread(data.unread_count);
      // El mismo canal mantiene al día el saldo del Header fuera de la mesa.
      if (event === 'wallet:update' && userId != null) window.dispatchEvent(new CustomEvent('palacetec:wallet-update', { detail: { userId, wallet: data } }));
    }) : () => {};
    return () => { active = false; stop(); };
  }, [live, userId]);
  useEffect(() => {
    if (!open) return undefined;
    const outside = (event) => { if (ref.current && !ref.current.contains(event.target)) setOpen(false); };
    const escape = (event) => { if (event.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', outside); document.addEventListener('keydown', escape);
    return () => { document.removeEventListener('mousedown', outside); document.removeEventListener('keydown', escape); };
  }, [open]);
  async function readAll() {
    // Actualización inmediata en pantalla; si el servidor falla se recupera el estado anterior.
    const previous = { items, unread };
    setItems((old) => old.map((item) => ({ ...item, is_read: true }))); setUnread(0);
    try { await markAllNotificationsRead(); } catch (err) { setItems(previous.items); setUnread(previous.unread); setError(err.message); }
  }
  async function readOne(item) {
    if (item.is_read) return;
    setItems((old) => old.map((other) => other.id === item.id ? { ...other, is_read: true } : other));
    setUnread((value) => Math.max(0, value - 1));
    try { setUnread((await markNotificationRead(item.id)).unread_count); } catch { /* el contador se corrige con la próxima carga */ }
  }
  return <div className="notification-center" ref={ref}>
    <button type="button" className="bell-button" aria-expanded={open} aria-haspopup="true"
      aria-label={unread ? `Notificaciones: ${unread} sin leer` : 'Notificaciones'} onClick={() => setOpen(!open)}>
      <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path fill="currentColor" d="M12 22a2.5 2.5 0 0 0 2.45-2h-4.9A2.5 2.5 0 0 0 12 22Zm7-6V11a7 7 0 0 0-5.5-6.84V3.5a1.5 1.5 0 0 0-3 0v.66A7 7 0 0 0 5 11v5l-2 2v1h18v-1Z" /></svg>
      {unread > 0 && <span className="bell-badge">{unread > 99 ? '99+' : unread}</span>}
    </button>
    {open && <div className="notification-panel" role="region" aria-label="Centro de notificaciones">
      <header><strong>Notificaciones</strong>
        <button type="button" className="link-button" disabled={!unread} onClick={readAll}>Marcar todas como leídas</button></header>
      {error && <p role="alert">{error}</p>}
      {items.length === 0 ? <p className="notification-empty">No tienes notificaciones.</p>
        : <ul>{items.map((item) => <li key={item.id} className={item.is_read ? 'read' : 'unread'}>
          <button type="button" onClick={() => readOne(item)}>
            <span className="notification-icon" aria-hidden="true">{ICONS[item.category] || '🔔'}</span>
            <span><strong>{item.title}</strong><span>{item.message}</span><small>{timeAgo(item.created_at)}</small></span>
            {!item.is_read && <i className="unread-dot" aria-label="Sin leer" />}
          </button></li>)}</ul>}
      <footer><Link to="/perfil/notificaciones" onClick={() => setOpen(false)}>Preferencias de notificación</Link></footer>
    </div>}
  </div>;
}
