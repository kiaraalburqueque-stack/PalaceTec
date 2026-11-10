import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { getAdminGames } from '../services/adminService';
import Loading from '../components/Loading';
import GameEditModal from '../components/GameEditModal';
import { categoryLabel } from '../utils/games';
import { credits } from '../utils/format';

export default function Games() {
  const [games, setGames] = useState(null);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState(null);
  const location = useLocation();
  const [notice, setNotice] = useState(location.state?.created ? `Se creó ${location.state.created}. Ya aparece en el catálogo del jugador.` : '');
  const [version, setVersion] = useState(0);
  useEffect(() => {
    let active = true;
    getAdminGames().then((data) => { if (active) setGames(data); }).catch((err) => { if (active) setError(err.message); });
    return () => { active = false; };
  }, [version]);
  const refresh = (message) => { setNotice(message); setVersion((v) => v + 1); };
  return <section className="page">
    <header className="page-header"><div><h1>Catálogo de juegos</h1><p>Crea juegos, ajusta sus límites y RTP o desactívalos sin perder el historial.</p></div>
      <Link className="primary button-link" to="/juegos/nuevo">+ Nuevo juego</Link></header>
    {notice && <p className="notice" role="status">{notice}</p>}
    {error && <p role="alert">{error}</p>}
    {!games && !error && <Loading />}
    {games && <div className="game-grid">{games.map((game) => <article key={game.id} className={`game-card ${game.deleted_at ? 'is-deleted' : ''}`}>
      <div className="game-cover">{game.cover_url ? <img src={game.cover_url} alt={`Portada de ${game.title}`} /> : <span aria-hidden="true">🎰</span>}</div>
      <div className="game-body">
        <div className="game-title"><h2>{game.title}</h2>
          <span className={`badge ${game.deleted_at ? 'badge-red' : game.is_published ? 'badge-green' : 'badge-gray'}`}>{game.deleted_at ? 'Desactivado' : game.is_published ? 'Publicado' : 'Borrador'}</span></div>
        <p className="muted">/{game.slug} · {categoryLabel(game.category)} · {game.rooms} sala{game.rooms === 1 ? '' : 's'}</p>
        <dl className="game-params"><div><dt>Mínima</dt><dd>{credits(game.min_bet)}</dd></div><div><dt>Máxima</dt><dd>{credits(game.max_bet)}</dd></div><div><dt>RTP</dt><dd>{game.rtp}%</dd></div></dl>
        <div className="actions">
          {!game.deleted_at && <button type="button" className="secondary small" onClick={() => setEditing(game)}>Editar parámetros</button>}
        </div>
      </div>
    </article>)}</div>}
    {editing && <GameEditModal game={editing} onClose={() => setEditing(null)} onSaved={(game) => { setEditing(null); refresh(`Se actualizaron los parámetros de ${game.title}. Las salas activas ya usan los nuevos límites.`); }} />}
  </section>;
}
