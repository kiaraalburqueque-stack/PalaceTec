import { API_BASE_URL, apiRequest, endSession, refreshSession } from './api';

export const getNotifications = () => apiRequest('/api/v1/notifications');
export const markAllNotificationsRead = () => apiRequest('/api/v1/notifications/read-all', { method: 'POST', body: '{}' });
export const markNotificationRead = (id) => apiRequest(`/api/v1/notifications/${id}/read`, { method: 'POST', body: '{}' });
export const getNotificationPreferences = () => apiRequest('/api/v1/users/notification-preferences');
export const updateNotificationPreferences = (data) => apiRequest('/api/v1/users/notification-preferences', { method: 'PUT', body: JSON.stringify(data) });

export function forceDisconnect() {
  // HU42: el administrador suspendió la cuenta; se cierra la sesión local de inmediato.
  sessionStorage.setItem('palacetec:notice', 'Tu cuenta fue suspendida por un administrador. Comunícate con soporte.');
  endSession();
}

// HU57: canal personal en tiempo real. Se reconecta con retroceso exponencial.
export function connectNotifications(onEvent) {
  let socket; let timer; let attempt = 0; let stopped = false;
  const renewed = () => { if (!stopped) { if (socket) socket.onclose = null; socket?.close(); clearTimeout(timer); attempt = 0; connect(); } };
  const ended = () => { stopped = true; clearTimeout(timer); socket?.close(); };
  window.addEventListener('palacetec:session-renewed', renewed);
  window.addEventListener('palacetec:session-ended', ended);
  function connect() {
    if (stopped) return;
    socket = new WebSocket(API_BASE_URL.replace(/^http/, 'ws') + '/ws/notifications/');
    socket.onopen = () => { attempt = 0; };
    socket.onmessage = ({ data }) => {
      try {
        const message = JSON.parse(data);
        if (message.event === 'FORCE_DISCONNECT') { stopped = true; forceDisconnect(); return; }
        onEvent(message);
      } catch { /* mensaje inválido: se ignora */ }
    };
    socket.onclose = ({ code }) => {
      if (stopped) return;
      if (code === 4403) { stopped = true; forceDisconnect(); return; }
      if (code === 4401) { void refreshSession().catch(() => {}); return; }
      const delay = Math.min(30000, 1000 * 2 ** attempt); attempt += 1;
      timer = setTimeout(connect, delay);
    };
  }
  connect();
  return () => { stopped = true; clearTimeout(timer); if (socket) socket.onclose = null; socket?.close();
    window.removeEventListener('palacetec:session-renewed', renewed); window.removeEventListener('palacetec:session-ended', ended); };
}
