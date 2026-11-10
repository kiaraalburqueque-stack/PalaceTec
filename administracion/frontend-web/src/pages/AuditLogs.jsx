import { useEffect, useState } from 'react';
import { getAuditLogs } from '../services/adminService';
import Pagination from '../components/Pagination';
import Loading from '../components/Loading';
import { dateTime } from '../utils/format';

// HU50: registro inmutable de las acciones de modificación de los administradores.
export default function AuditLogs() {
  const [search, setSearch] = useState('');
  const [filters, setFilters] = useState({ search: '', method: '' });
  const [page, setPage] = useState(1);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState(null);
  useEffect(() => {
    let active = true;
    getAuditLogs({ ...filters, page }).then((value) => { if (active) { setData(value); setError(''); } })
      .catch((err) => { if (active) setError(err.message); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [filters, page]);
  const apply = (next) => { setLoading(true); setPage(1); setFilters((old) => ({ ...old, ...next })); };
  return <section className="page">
    <header className="page-header"><div><h1>Registro de Auditoría del Sistema</h1><p>Cada POST, PUT, PATCH o DELETE de un administrador queda registrado y no se puede editar.</p></div></header>
    <form className="toolbar" onSubmit={(e) => { e.preventDefault(); apply({ search }); }}>
      <input type="search" aria-label="Buscar en auditoría" placeholder="Administrador, acción, recurso o IP" value={search} onChange={(e) => setSearch(e.target.value)} />
      <select aria-label="Método" value={filters.method} onChange={(e) => apply({ method: e.target.value })}>
        <option value="">Todos los métodos</option>{['POST', 'PUT', 'PATCH', 'DELETE'].map((m) => <option key={m}>{m}</option>)}</select>
      <button className="primary">Buscar</button>
    </form>
    {error && <p role="alert">{error}</p>}
    {!data && !error && <Loading />}
    {data && <div className="table-card"><table>
      <thead><tr><th>Fecha (Lima)</th><th>Administrador</th><th>Acción</th><th>Recurso</th><th>Resultado</th><th>IP</th><th>Datos</th></tr></thead>
      <tbody>{data.results.length === 0 ? <tr><td colSpan={7} className="empty">Sin registros.</td></tr>
        : data.results.map((log) => <tr key={log.id}>
          <td className="nowrap">{dateTime(log.created_at)}</td><td>{log.admin}</td>
          <td><span className={`method method-${log.method.toLowerCase()}`}>{log.method}</span> {log.action.replace(`${log.method} `, '')}</td>
          <td><code>{log.resource}</code></td>
          <td><span className={`badge ${log.status_code < 400 ? 'badge-green' : 'badge-red'}`}>{log.status_code}</span></td>
          <td>{log.ip_address || '—'}</td>
          <td>{Object.keys(log.details || {}).length ? <button type="button" className="link" aria-expanded={open === log.id} onClick={() => setOpen(open === log.id ? null : log.id)}>{open === log.id ? 'Ocultar' : 'Ver'}</button> : '—'}
            {open === log.id && <pre className="details">{JSON.stringify(log.details, null, 2)}</pre>}</td>
        </tr>)}</tbody></table>
      <Pagination page={page} count={data.count} onChange={(value) => { setLoading(true); setPage(value); }} disabled={loading} />
    </div>}
  </section>;
}
