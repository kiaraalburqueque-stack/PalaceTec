import { useEffect, useRef, useState } from 'react';
import { connectRoom } from '../services/roomSocket';

// HU59: conecta la sala y expone el estado de la conexión sin desmontar la pantalla al reconectar.
export default function useRoomSocket(roomId, onEvent, enabled = true) {
  const [status, setStatus] = useState('Conectando');
  const [recovered, setRecovered] = useState(null);
  const handler = useRef(onEvent);
  useEffect(() => { handler.current = onEvent; }, [onEvent]);
  useEffect(() => {
    if (!enabled || !roomId) return undefined;
    let active = true;
    const stop = connectRoom(roomId, (message) => {
      if (!active) return;
      if (message.event === 'RECONNECT_SUCCESS') setRecovered(message.data);
      handler.current?.(message);
    }, (value) => { if (active) setStatus(value); });
    return () => { active = false; stop(); };
  }, [roomId, enabled]);
  return { status, reconnecting: status === 'Reconectando', recovered, dismissRecovered: () => setRecovered((old) => old && { ...old, dismissed: true }) };
}
