import { API_BASE_URL, refreshSession, endSession } from './api';

export function connectRoom(roomId, onEvent, onStatus) {
  let socket;
  let timer;
  let stopped = false;
  const renewed = () => { if (!stopped) { if (socket) socket.onclose = null; socket?.close(); clearTimeout(timer); connect(); } };
  const ended = () => { stopped = true; clearTimeout(timer); socket?.close(); onStatus('Sesión finalizada'); };
  window.addEventListener('palacetec:session-renewed', renewed);
  window.addEventListener('palacetec:session-ended', ended);
  function connect() {
    if (stopped) return;
    onStatus('Conectando');
    socket = new WebSocket(API_BASE_URL.replace(/^http/, 'ws') + '/ws/roulette/' + roomId + '/');
    socket.onopen = () => { onStatus('Sincronizando'); socket.send(JSON.stringify({ event: 'room:sync' })); };
    socket.onmessage = ({ data }) => { try { const message = JSON.parse(data); if (message.event === 'session:revoked') { endSession(); return; } if (message.event === 'room:state') onStatus('En vivo'); onEvent(message); } catch { onStatus('Mensaje inválido'); } };
    socket.onerror = () => onStatus('Reconectando');
    socket.onclose = ({ code }) => {
      if (stopped) return;
      if (code === 4401) { onStatus('Renovando sesión'); void refreshSession().catch(() => {}); return; }
      if (code === 4404 || code === 4403) { onStatus('Sala no disponible o acceso denegado'); return; }
      onStatus('Reconectando');
      timer = setTimeout(connect, 2000);
    };
  }
  connect();
  return () => { stopped = true; clearTimeout(timer); socket?.close(); window.removeEventListener('palacetec:session-renewed', renewed); window.removeEventListener('palacetec:session-ended', ended); };
}
