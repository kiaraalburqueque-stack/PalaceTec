import { useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import useUser from '../hooks/useUser';
import { getBetHistory } from '../services/historyService';
import DateRangeFilter from '../components/DateRangeFilter';
const statuses = { PENDING: 'Pendiente', WON: 'Ganada', LOST: 'Perdida' };
export default function HistoryPage() {
  const { user, loading: sessionLoading } = useUser();
  const [page, setPage] = useState(1);
  const [filters, setFilters] = useState({});
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  useEffect(() => {
    if (!user) return;
    let active = true;
    getBetHistory({ page, page_size: 20, ...filters }).then((value) => { if (active) { setData(value); setError(''); } })
      .catch((err) => { if (active) setError(err.message); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [user, page, filters]);
  function go(value) { setLoading(true); setPage(value); }
  if (sessionLoading) return <p role="status">Consultando sesión...</p>;
  if (!user) return <Navigate to="/login" replace />;
  return <main className="main-sections"><section className="bet-controls history-panel"><h1>Mi historial de apuestas</h1>
    <DateRangeFilter disabled={loading} onApply={(value) => { setLoading(true); setPage(1); setFilters(value); }} />
    {loading && <p role="status">Consultando apuestas...</p>}
    {error && <p role="alert">{error}</p>}
    {!loading && !error && data && <>
      <p>{data.count} apuestas · Página {page}</p>
      {!data.results.length ? <p>No hay apuestas en este período.</p> : <div className="table-scroll"><table><caption>Apuestas de tu cuenta</caption><thead><tr><th>Fecha (Lima)</th><th>Juego / sala</th><th>Ronda</th><th>Apuesta</th><th>Monto</th><th>Estado</th><th>Pago total</th><th>Neto</th></tr></thead>
        <tbody>{data.results.map((bet) => <tr key={bet.id}><td>{new Date(bet.created_at).toLocaleString('es-PE', { timeZone: 'America/Lima' })}</td><td>{bet.game} / {bet.room_id}</td><td>{bet.round_id}</td><td>{bet.kind}: {bet.selection}</td><td>{bet.amount} CRD</td><td>{statuses[bet.status]}</td><td>{bet.payout == null ? 'Pendiente' : `${bet.payout} CRD`}</td><td>{bet.net == null ? 'Pendiente' : `${bet.net} CRD`}</td></tr>)}</tbody></table></div>}
      <nav aria-label="Paginación de historial"><button disabled={!data.previous || loading} onClick={() => go(page - 1)}>Anterior</button><button disabled={!data.next || loading} onClick={() => go(page + 1)}>Siguiente</button></nav>
    </>}
  </section></main>;
}
