import { API_BASE_URL, refreshSession, endSession } from './api';

// HU59: retroceso exponencial (1 s, 2 s, 4 s… hasta 30 s) con una pequeña variación aleatoria.
export function reconnectDelay(attempt, random = Math.random) {
  const base = Math.min(30000, 1000 * 2 ** attempt);
  return Math.round(base * (0.85 + random() * 0.15));
}

export function connectRoom(roomId, onEvent, onStatus) {
  let socket;
  let timer;
  let stopped = false;
  let attempt = 0;
  let connectedBefore = false;
  const renewed = () => { if (!stopped) { if (socket) socket.onclose = null; socket?.close(); clearTimeout(timer); connect(); } };
  const ended = () => { stopped = true; clearTimeout(timer); socket?.close(); onStatus('Sesión finalizada'); };
  window.addEventListener('palacetec:session-renewed', renewed);
  window.addEventListener('palacetec:session-ended', ended);
  function connect() {
    if (stopped) return;
    onStatus(attempt ? 'Reconectando' : 'Conectando');
    socket = new WebSocket(API_BASE_URL.replace(/^http/, 'ws') + '/ws/roulette/' + roomId + '/');
    socket.onopen = () => {
      onStatus('Sincronizando');
      socket.send(JSON.stringify({ event: 'room:sync' }));
      // Pide la ronda en curso y las apuestas propias para restaurarlas tras una caída.
      socket.send(JSON.stringify({ event: 'room:resume' }));
      attempt = 0;
    };
    socket.onmessage = ({ data }) => {
      try {
        const message = JSON.parse(data);
        if (message.event === 'session:revoked') { endSession(); return; }
        if (message.event === 'FORCE_DISCONNECT') {
          stopped = true;
          sessionStorage.setItem('palacetec:notice', 'Tu cuenta fue suspendida por un administrador. Comunícate con soporte.');
          endSession(); return;
        }
        if (message.event === 'room:state') onStatus('En vivo');
        if (message.event === 'RECONNECT_SUCCESS') { message.data = { ...message.data, reconnected: connectedBefore }; connectedBefore = true; }
        onEvent(message);
      } catch { onStatus('Mensaje inválido'); }
    };
    socket.onerror = () => onStatus('Reconectando');
    socket.onclose = ({ code }) => {
      if (stopped) return;
      if (code === 4401) { onStatus('Renovando sesión'); void refreshSession().catch(() => {}); return; }
      if (code === 4403) { onStatus('Acceso denegado'); return; }
      if (code === 4404) { onStatus('Sala no disponible o acceso denegado'); return; }
      connectedBefore = true;
      const delay = reconnectDelay(attempt);
      attempt += 1;
      onStatus('Reconectando');
      timer = setTimeout(connect, delay);
    };
  }
  connect();
  return () => { stopped = true; clearTimeout(timer); socket?.close(); window.removeEventListener('palacetec:session-renewed', renewed); window.removeEventListener('palacetec:session-ended', ended); };
}
