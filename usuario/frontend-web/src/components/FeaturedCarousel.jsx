import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { getFeaturedGames } from '../services/roomService';

const CATEGORIES = { RULETA: 'Ruleta', CARTAS: 'Cartas', DEPORTES: 'Deportes virtuales', OTROS: 'Otros' };
const AUTOPLAY_MS = 6000;

// HU64: carrusel de juegos destacados por el administrador (máximo 5).
export default function FeaturedCarousel() {
  const [games, setGames] = useState([]);
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  useEffect(() => {
    let active = true;
    getFeaturedGames().then((data) => { if (active) setGames(data); }).catch(() => {});
    return () => { active = false; };
  }, []);
  useEffect(() => {
    if (paused || games.length < 2) return undefined;
    const timer = setInterval(() => setIndex((value) => (value + 1) % games.length), AUTOPLAY_MS);
    return () => clearInterval(timer);
  }, [paused, games.length]);
  if (!games.length) return null;
  const current = games[index % games.length];
  const target = current.play_path || (current.play_room_id ? `/ruleta/${current.play_room_id}` : null);
  const go = (step) => setIndex((value) => (value + step + games.length) % games.length);
  return <section className="featured-carousel" aria-roledescription="carrusel" aria-label="Juegos destacados"
    onMouseEnter={() => setPaused(true)} onMouseLeave={() => setPaused(false)} onFocus={() => setPaused(true)} onBlur={() => setPaused(false)}>
    <article key={current.id} className="featured-slide" aria-roledescription="diapositiva" aria-label={`${index % games.length + 1} de ${games.length}`}
      style={current.cover_url ? { backgroundImage: `linear-gradient(90deg, #0d1015ee 35%, #0d101555), url(${current.cover_url})` } : undefined}>
      <span className="eyebrow">★ DESTACADO · {CATEGORIES[current.category] || current.category}</span>
      <h2>{current.title}</h2>
      <p>Apuestas desde {current.min_bet} hasta {current.max_bet} CRD{current.rtp ? ` · RTP ${current.rtp}%` : ''}</p>
      {target && <Link className="primary-action" to={target}>Jugar {current.title}</Link>}
    </article>
    {games.length > 1 && <div className="featured-controls">
      <button type="button" onClick={() => go(-1)} aria-label="Destacado anterior">‹</button>
      <div className="featured-dots">{games.map((game, position) => <button key={game.id} type="button"
        aria-label={`Ver ${game.title}`} aria-current={position === index % games.length} onClick={() => setIndex(position)} />)}</div>
      <button type="button" onClick={() => go(1)} aria-label="Destacado siguiente">›</button>
    </div>}
  </section>;
}
