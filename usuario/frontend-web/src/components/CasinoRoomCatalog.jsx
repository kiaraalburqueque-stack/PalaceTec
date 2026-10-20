
import { useEffect, useState } from 'react';
import CasinoRoomCard from './CasinoRoomCard';
import { getCasinoRooms } from '../services/roomService';
import { useNavigate } from 'react-router-dom';

export default function CasinoRoomCatalog({ gameId }) {
  const navigate = useNavigate();
  const [rooms, setRooms] = useState([]);
  const [selectedRoom, setSelectedRoom] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;

    async function loadRooms() {
      try {
        const data = await getCasinoRooms(gameId);

        if (!active) return;

        setRooms(
          data.map((room) => ({
            ...room,
            description: `Apuesta mínima: ${room.min_bet} CRD · Máxima: ${room.max_bet} CRD`,
            game: 'Ruleta',
            status: 'Disponible',
          }))
        );
      } catch (err) {
        if (active) {
          setError(err.message);
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadRooms();

    return () => {
      active = false;
    };
  }, [gameId]);

  const handleSelectRoom = (room) => {
    setSelectedRoom(room);
    navigate(`/ruleta/${room.id}`);
  };

  return (
    <section className="casino-catalog">
      <h2>Salas de Casino</h2>
      <p>Selecciona una sala para comenzar a jugar.</p>

      {loading && <p role="status">Cargando salas...</p>}

      {error && <p role="alert">{error}</p>}

      {!loading && !error && rooms.length === 0 && (
        <p>No hay salas disponibles actualmente.</p>
      )}

      <div className="casino-room-grid">
        {rooms.map((room) => (
          <CasinoRoomCard
            key={room.id}
            room={room}
            onSelect={handleSelectRoom}
          />
        ))}
      </div>

      {selectedRoom && (
        <p className="selected-room-message" role="status">
          Sala seleccionada: {selectedRoom.name}
        </p>
      )}
    </section>
  );
}
