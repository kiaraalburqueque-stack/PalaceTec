import { useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import useUser from '../hooks/useUser';
import { getStatsSummary } from '../services/historyService';
import DateRangeFilter from '../components/DateRangeFilter';
export default function StatsPage() {
  const { user, loading: sessionLoading } = useUser();
  const [filters, setFilters] = useState({});
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  useEffect(() => {
    if (!user) return;
    let active = true;
    getStatsSummary(filters).then((value) => { if (active) { setData(value); setError(''); } })
      .catch((err) => { if (active) setError(err.message); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [user, filters]);
  if (sessionLoading) return <p role="status">Consultando sesión...</p>;
  if (!user) return <Navigate to="/login" replace />;
  return <main className="main-sections"><section className="bet-controls"><h1>Mis estadísticas</h1>
    <p>Solo apuestas liquidadas de tu cuenta. Los montos pendientes no se incluyen.</p>
    <DateRangeFilter disabled={loading} onApply={(value) => { setLoading(true); setFilters(value); }} />
    {loading && <p role="status">Consultando estadísticas...</p>}
    {error && <p role="alert">{error}</p>}
    {!loading && !error && data && <dl className="stats-grid"><div><dt>Total apostado</dt><dd>{data.total_wagered} CRD</dd></div><div><dt>Pago total</dt><dd>{data.total_paid} CRD</dd></div><div><dt>Resultado neto</dt><dd>{data.net} CRD</dd></div></dl>}
    <small>Pago total incluye la devolución de la apuesta. Neto = pago total − monto apostado. Fechas en Lima.</small>
  </section></main>;
}
