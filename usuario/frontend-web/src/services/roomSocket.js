import { API_BASE_URL } from './api';

export function connectRoom(roomId, onEvent, onStatus) {
  let socket;
  let timer;
  let stopped = false;
  function connect() {
    if (stopped) return;
    onStatus('Conectando');
    socket = new WebSocket(API_BASE_URL.replace(/^http/, 'ws') + '/ws/roulette/' + roomId + '/');
    socket.onopen = () => { onStatus('En vivo'); socket.send(JSON.stringify({ event: 'room:sync' })); };
    socket.onmessage = ({ data }) => { try { onEvent(JSON.parse(data)); } catch { onStatus('Mensaje inválido'); } };
    socket.onerror = () => onStatus('Reconectando');
    socket.onclose = ({ code }) => {
      if (stopped) return;
      if (code === 4401 || code === 4404) { onStatus('Sesión vencida o sala no disponible'); return; }
      onStatus('Reconectando');
      timer = setTimeout(connect, 2000);
    };
  }
  connect();
  return () => { stopped = true; clearTimeout(timer); socket?.close(); };
}
