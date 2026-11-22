import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { getGameCategories, getGames } from '../services/roomService';
import CasinoRoomCatalog from './CasinoRoomCatalog';
import CategoryTabs from './CategoryTabs';
import GameRulesModal from './GameRulesModal';
const CATEGORIES = { RULETA: 'Ruleta', CARTAS: 'Cartas', DEPORTES: 'Deportes virtuales', OTROS: 'Otros' };

export default function GameCatalog() {
  // HU61: la categoría elegida vive en la URL (?categoria=cartas) y se filtra sin recargar la página.
  const [searchParams, setSearchParams] = useSearchParams();
  const category = searchParams.get('categoria') || '';
  const [games, setGames] = useState(null);
  const [counts, setCounts] = useState(null);
  const [selected, setSelected] = useState(null);
  const [error, setError] = useState('');
  const [rulesFor, setRulesFor] = useState(null);
  useEffect(() => {
    let active = true;
    getGameCategories().then((data) => { if (active) setCounts(data); }).catch(() => {});
    return () => { active = false; };
  }, []);
  useEffect(() => {
    let active = true;
    getGames({ category }).then((data) => {
      if (!active) return;
      setGames(data); setError('');
      setSelected((current) => data.some((game) => game.id === current && game.rooms_url) ? current
        : data.find((game) => !game.play_path)?.id ?? null);
    }).catch((err) => { if (active) setError(err.message); });
    return () => { active = false; };
  }, [category]);
  function changeCategory(slug) {
    const next = new URLSearchParams(searchParams);
    if (slug) next.set('categoria', slug); else next.delete('categoria');
    setSearchParams(next);
  }
  const tabs = [{ slug: '', label: 'Todos', count: counts?.total },
    ...(counts?.categories || []).map((item) => ({ slug: item.slug, label: item.label, count: item.count }))];
  return <section className="casino-catalog"><h2>Juegos disponibles</h2>
    {counts && <CategoryTabs tabs={tabs} active={category} onChange={changeCategory} />}
    {error && <p role="alert">{error}</p>}
    {!games && !error && <p role="status">Consultando juegos...</p>}
    {games?.length === 0 && <p>No hay juegos publicados en esta categoría.</p>}
    <div className="casino-room-grid catalog-fade" key={category}>{games?.map((game) => <article className="bet-controls" key={game.id}>
      {game.cover_url && <img className="game-cover" src={game.cover_url} alt={`Portada de ${game.title}`} loading="lazy" />}
      <h3>{game.title}</h3>
      {game.category && <p className="game-meta"><span className="game-category">{CATEGORIES[game.category] || game.category}</span>{game.rtp && <span>RTP {game.rtp}%</span>}</p>}
      <p>Desde {game.min_bet} hasta {game.max_bet} CRD</p>
      <div className="game-card-actions">
        {game.play_path ? <Link className="rules-button play-link" to={game.play_path}>Jugar {game.title}</Link>
          : <button aria-pressed={selected === game.id} onClick={() => setSelected(game.id)}>Ver salas de {game.title}</button>}
        <button type="button" className="rules-button" onClick={() => setRulesFor(game.id)}>Reglas y pagos de {game.title}</button>
      </div>
    </article>)}</div>
    {rulesFor != null && <GameRulesModal gameId={rulesFor} onClose={() => setRulesFor(null)} />}
    {selected != null && games?.some((game) => game.id === selected) && <CasinoRoomCatalog key={selected} gameId={selected} />}
  </section>;
}
