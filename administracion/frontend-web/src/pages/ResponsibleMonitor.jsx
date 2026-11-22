import { useEffect, useState } from 'react';
import { getResponsibleUsers } from '../services/adminService';
import Pagination from '../components/Pagination';
import Loading from '../components/Loading';
import { credits, dateTime } from '../utils/format';

// HU46: monitoreo de juego responsable con insignias de estado.
const BADGES = { LIMIT_ACTIVE: ['Límite activo', 'badge-blue'], SELF_EXCLUDED: ['Autoexcluido', 'badge-purple'], RISK_ALERT: ['Con alerta de riesgo', 'badge-red'] };

export default function ResponsibleMonitor() {
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(1);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true;
    getResponsibleUsers({ status, page }).then((value) => { if (active) { setData(value); setError(''); } })
      .catch((err) => { if (active) setError(err.message); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [status, page]);
  const pick = (value) => { setLoading(true); setPage(1); setStatus(value); };
  return <section className="page">
    <header className="page-header"><div><h1>Monitoreo de Juego Responsable</h1><p>Jugadores con límites diarios, autoexclusiones o alertas de riesgo de los últimos 30 días.</p></div></header>
    {data?.summary && <div className="summary-cards" role="group" aria-label="Filtrar por estado">
      {[['', 'Todos', data.count && !status ? data.count : null], ['LIMIT_ACTIVE', 'Límite activo', data.summary.limit_active], ['SELF_EXCLUDED', 'Autoexcluidos', data.summary.self_excluded], ['RISK_ALERT', 'Con alerta', data.summary.risk_alert]].map(([value, label, total]) =>
        <button type="button" key={value || 'all'} aria-pressed={status === value} className={`summary-card ${status === value ? 'active' : ''}`} onClick={() => pick(value)}>
          <span>{label}</span>{total != null && <strong>{total}</strong>}</button>)}
    </div>}
    {error && <p role="alert">{error}</p>}
    {!data && !error && <Loading />}
    {data && <div className="table-card"><table>
      <thead><tr><th>Jugador</th><th>Estado</th><th>Límite diario</th><th>Gastado hoy</th><th>Autoexclusión hasta</th><th>Alertas (30 días)</th><th>Última alerta</th></tr></thead>
      <tbody>{data.results.length === 0 ? <tr><td colSpan={7} className="empty">No hay jugadores en este estado.</td></tr>
        : data.results.map((row) => <tr key={row.id}>
          <td>{row.email}<small className="muted"> · DNI {row.dni}</small>{!row.is_active && <span className="badge badge-gray"> Suspendido</span>}</td>
          <td className="badges">{row.statuses.map((key) => <span key={key} className={`badge ${BADGES[key][1]}`}>{BADGES[key][0]}</span>)}</td>
          <td>{row.daily_limit ? credits(row.daily_limit) : '—'}</td>
          <td>{row.daily_limit ? <><span>{credits(row.spent_today)}</span><div className="meter" title={`${row.remaining_today} CRD disponibles`}><i style={{ width: `${Math.min(100, (Number(row.spent_today) / Math.max(1, Number(row.daily_limit))) * 100)}%` }} /></div></> : credits(row.spent_today)}</td>
          <td>{row.self_excluded_until ? dateTime(row.self_excluded_until) : '—'}</td>
          <td>{row.risk_alerts}{row.pause_until && <small className="muted"> · en pausa</small>}</td>
          <td>{dateTime(row.last_alert_at)}</td>
        </tr>)}</tbody></table>
      <Pagination page={page} count={data.count} onChange={(value) => { setLoading(true); setPage(value); }} disabled={loading} />
    </div>}
  </section>;
}
