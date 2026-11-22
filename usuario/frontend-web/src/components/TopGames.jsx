import { useEffect, useState } from 'react';
import { getTopGames } from '../services/statsService';

// HU67: ranking de los 5 juegos con más rondas y créditos apostados, con barras horizontales.
export default function TopGames() {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    getTopGames().then((value) => { if (active) setData(value); }).catch((err) => { if (active) setError(err.message); });
    return () => { active = false; };
  }, []);
  const max = Math.max(1, ...(data?.results || []).map((item) => item.rounds));
  return <section className="stats-card top-games" aria-labelledby="top-games-title">
    <h2 id="top-games-title">Tus juegos más jugados</h2>
    {error && <p role="alert">{error}</p>}
    {!data && !error && <p role="status">Consultando juegos...</p>}
    {data && !data.results.length && <p>Aún no tienes juegos jugados.</p>}
    {data?.results.length > 0 && <ol className="top-games-list">{data.results.map((item) => <li key={item.game_id}>
      <div className="top-games-label"><strong>{item.title}</strong><span>{item.rounds} {item.rounds === 1 ? 'ronda' : 'rondas'} · {item.wagered} CRD</span></div>
      <div className="top-games-bar" role="presentation"><span style={{ width: `${(item.rounds / max) * 100}%` }} /></div>
    </li>)}</ol>}
  </section>;
}
