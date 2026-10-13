
import { useState } from 'react';
import CasinoRoomCard from './CasinoRoomCard';

const demoRooms = [
  {
    id: 1,
    name: 'Ruleta Clásica',
    description: 'Disfruta de la ruleta europea con números del 0 al 36.',
    game: 'Ruleta',
    status: 'Disponible',
  },
  {
    id: 2,
    name: 'Ruleta VIP',
    description: 'Una sala especial para jugadores experimentados.',
    game: 'Ruleta',
    status: 'Disponible',
  },
  {
    id: 3,
    name: 'Sala Premium',
    description: 'Próximamente más experiencias de casino virtual.',
    game: 'Próximamente',
    status: 'No disponible',
  },
];

export default function CasinoRoomCatalog() {
  const [selectedRoom, setSelectedRoom] = useState(null);

  const handleSelectRoom = (room) => {
    setSelectedRoom(room);
  };

  return (
    <section className="casino-catalog">
      <h2>Salas de Casino</h2>
      <p>Selecciona una sala para comenzar a jugar.</p>

      <div className="casino-room-grid">
        {demoRooms.map((room) => (
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
