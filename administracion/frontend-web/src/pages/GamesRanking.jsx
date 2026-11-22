import { useEffect, useState } from 'react';
import { getGamesRanking } from '../services/adminService';
import Loading from '../components/Loading';
import { categoryLabel } from '../utils/games';
import { credits } from '../utils/format';

// HU71: rondas, jugadores únicos y créditos apostados por juego, ordenados por créditos.
export default function GamesRanking() {
  const [days, setDays] = useState(7);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    getGamesRanking(days).then((value) => { if (active) { setData(value); setError(''); } })
      .catch((err) => { if (active) setError(err.message); });
    return () => { active = false; };
  }, [days]);
  const max = Math.max(1, ...(data?.games || []).map((game) => Number(game.wagered)));
  return <section className="page">
    <header className="page-header"><div><h1>Ranking de juegos</h1><p>Popularidad por créditos apostados, rondas jugadas y jugadores únicos (hora de Lima).</p></div>
      <div className="segmented" role="group" aria-label="Período del ranking">{[7, 30].map((value) => <button key={value} type="button"
        className={days === value ? 'is-active' : ''} aria-pressed={days === value} onClick={() => setDays(value)}>Últimos {value} días</button>)}</div>
    </header>
    {error && <p role="alert">{error}</p>}
    {!data && !error && <Loading />}
    {data && <>
      <p className="muted">Del {data.from} al {data.to} · {data.totals.rounds} rondas · {data.totals.unique_players} jugadores · {credits(data.totals.wagered)}</p>
      <div className="table-card"><table><caption className="sr-only">Ranking de juegos de los últimos {data.days} días</caption>
        <thead><tr><th>#</th><th>Juego</th><th>Rondas</th><th>Jugadores únicos</th><th>Créditos apostados</th></tr></thead>
        <tbody>{data.games.map((game, index) => <tr key={game.game_id}>
          <td>{index + 1}</td>
          <td><strong>{game.title}</strong> <span className="muted">· {categoryLabel(game.category)}</span>{!game.active && <span className="badge badge-gray"> Inactivo</span>}</td>
          <td>{game.rounds}</td><td>{game.unique_players}</td>
          <td className="ranking-credits"><span>{credits(game.wagered)} <small className="muted">({game.share_pct}%)</small></span>
            <span className="ranking-bar" aria-hidden="true"><i style={{ width: `${(Number(game.wagered) / max) * 100}%` }} /></span></td>
        </tr>)}</tbody></table></div>
    </>}
  </section>;
}
