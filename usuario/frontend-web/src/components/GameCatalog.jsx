import { useEffect, useState } from 'react';
import { getGames } from '../services/roomService';
import CasinoRoomCatalog from './CasinoRoomCatalog';
import GameRulesModal from './GameRulesModal';
export default function GameCatalog() {
  const [games, setGames] = useState(null);
  const [selected, setSelected] = useState(null);
  const [error, setError] = useState('');
  const [rulesFor, setRulesFor] = useState(null);
  useEffect(() => {
    let active = true;
    getGames().then((data) => { if (active) { setGames(data); setSelected(data[0]?.id ?? null); } })
      .catch((err) => { if (active) setError(err.message); });
    return () => { active = false; };
  }, []);
  return <section className="casino-catalog"><h2>Juegos disponibles</h2>
    {error && <p role="alert">{error}</p>}
    {!games && !error && <p role="status">Consultando juegos...</p>}
    {games?.length === 0 && <p>No hay juegos publicados con salas disponibles.</p>}
    <div className="casino-room-grid">{games?.map((game) => <article className="bet-controls" key={game.id}>
      <h3>{game.title}</h3><p>Desde {game.min_bet} hasta {game.max_bet} CRD</p>
      <div className="game-card-actions">
        <button aria-pressed={selected === game.id} onClick={() => setSelected(game.id)}>Ver salas de {game.title}</button>
        <button type="button" className="rules-button" onClick={() => setRulesFor(game.id)}>Reglas y pagos de {game.title}</button>
      </div>
    </article>)}</div>
    {rulesFor != null && <GameRulesModal gameId={rulesFor} onClose={() => setRulesFor(null)} />}
    {selected != null && <CasinoRoomCatalog key={selected} gameId={selected} />}
  </section>;
}
