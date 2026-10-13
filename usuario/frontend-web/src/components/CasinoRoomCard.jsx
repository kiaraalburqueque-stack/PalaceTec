
export default function CasinoRoomCard({ room, onSelect }) {
  return (
    <article className="casino-room-card">
      <div className="room-art" aria-hidden="true"><span className="room-live">● EN VIVO</span><div className="mini-wheel">♛</div><span className="room-edition">EUROPEAN ROULETTE</span></div>

      <h3>{room.name}</h3>
      <p>{room.description}</p>

      <div className="room-details">
        <span>Juego: {room.game}</span>
        <span>Estado: {room.status}</span>
      </div>

      <button
        type="button"
        className="enter-room-button"
        onClick={() => onSelect(room)}
        disabled={room.status !== 'Disponible'}
      >
        {room.status === 'Disponible'
          ? 'Entrar a la sala'
          : 'No disponible'}
      </button>
    </article>
  );
}
