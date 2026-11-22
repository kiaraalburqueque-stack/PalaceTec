import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getRecommendations } from '../services/roomService';

const CATEGORIES = { RULETA: 'Ruleta', CARTAS: 'Cartas', DEPORTES: 'Deportes virtuales', OTROS: 'Otros' };
const BLOCKED = {
  daily_limit_reached: 'Alcanzaste tu límite diario. Las recomendaciones vuelven mañana.',
  pause_active: 'Estás en una pausa de juego. Las recomendaciones vuelven cuando termine.',
  self_excluded: 'Tu cuenta está autoexcluida, así que no mostramos recomendaciones.',
};

// HU70: sección Para ti con hasta 4 juegos y el motivo de cada uno.
export default function ForYou() {
  const [data, setData] = useState(null);
  useEffect(() => {
    let active = true;
    getRecommendations().then((value) => { if (active) setData(value); }).catch(() => { if (active) setData(null); });
    return () => { active = false; };
  }, []);
  if (!data) return null;
  if (data.blocked) return <section className="for-you" aria-labelledby="for-you-title"><h2 id="for-you-title">Para ti</h2>
    <p className="for-you-blocked" role="status">{BLOCKED[data.blocked] || 'Hoy no mostramos recomendaciones.'}</p></section>;
  if (!data.results.length) return null;
  return <section className="for-you" aria-labelledby="for-you-title">
    <div className="section-heading"><div><span className="eyebrow">{data.personalized ? 'SEGÚN TU ACTIVIDAD' : 'LO MÁS JUGADO'}</span>
      <h2 id="for-you-title">Para ti</h2></div></div>
    <div className="for-you-grid">{data.results.map((game) => {
      const target = game.play_path || (game.play_room_id ? `/ruleta/${game.play_room_id}` : null);
      return <article key={game.id} className="for-you-card">
        <span className="game-category">{CATEGORIES[game.category] || game.category}</span>
        <h3>{game.title}</h3>
        <p>{game.reason}</p>
        {target && <Link to={target} className="rules-button play-link">Jugar {game.title}</Link>}
      </article>;
    })}</div>
  </section>;
}
