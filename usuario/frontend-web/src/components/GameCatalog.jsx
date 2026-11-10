import { useEffect, useState } from 'react';
import { getGames } from '../services/roomService';
import CasinoRoomCatalog from './CasinoRoomCatalog';
import GameRulesModal from './GameRulesModal';
const CATEGORIES = { RULETA: 'Ruleta', CARTAS: 'Cartas', DEPORTES: 'Deportes virtuales', OTROS: 'Otros' };

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
      {game.cover_url && <img className="game-cover" src={game.cover_url} alt={`Portada de ${game.title}`} loading="lazy" />}
      <h3>{game.title}</h3>
      {game.category && <p className="game-meta"><span className="game-category">{CATEGORIES[game.category] || game.category}</span>{game.rtp && <span>RTP {game.rtp}%</span>}</p>}
      <p>Desde {game.min_bet} hasta {game.max_bet} CRD</p>
      <div className="game-card-actions">
        <button aria-pressed={selected === game.id} onClick={() => setSelected(game.id)}>Ver salas de {game.title}</button>
        <button type="button" className="rules-button" onClick={() => setRulesFor(game.id)}>Reglas y pagos de {game.title}</button>
      </div>
    </article>)}</div>
    {rulesFor != null && <GameRulesModal gameId={rulesFor} onClose={() => setRulesFor(null)} />}
    {selected != null && <CasinoRoomCatalog key={selected} gameId={selected} />}
  </section>;
}
