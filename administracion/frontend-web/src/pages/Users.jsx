import { useEffect, useState } from 'react';
import { getUsers } from '../services/adminService';
import Pagination from '../components/Pagination';
import Loading from '../components/Loading';
import SanctionModal from '../components/SanctionModal';
import { credits, date } from '../utils/format';

// HU41: tabla paginada (10 por página) con filtros por estado y rol, búsqueda y orden.
const COLUMNS = [['id', 'ID'], ['email', 'Correo'], ['dni', 'DNI'], [null, 'Rol'], [null, 'Estado'], [null, 'Saldo'], ['created_at', 'Registro']];

export default function Users() {
  const [filters, setFilters] = useState({ search: '', status: '', role: '', ordering: '-created_at' });
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [target, setTarget] = useState(null);
  const [version, setVersion] = useState(0);
  useEffect(() => {
    let active = true;
    getUsers({ ...filters, page }).then((value) => { if (active) { setData(value); setError(''); } })
      .catch((err) => { if (active) setError(err.message); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [filters, page, version]);
  // La búsqueda espera 350 ms después de la última tecla para no consultar en cada letra.
  useEffect(() => {
    if (search === filters.search) return undefined;
    const timer = setTimeout(() => { setLoading(true); setPage(1); setFilters((old) => ({ ...old, search })); }, 350);
    return () => clearTimeout(timer);
  }, [search, filters.search]);
  const change = (key, value) => { setLoading(true); setPage(1); setFilters((old) => ({ ...old, [key]: value })); };
  const sortBy = (field) => change('ordering', filters.ordering === field ? `-${field}` : field);
  const arrow = (field) => filters.ordering === field ? ' ▲' : filters.ordering === `-${field}` ? ' ▼' : '';
  return <section className="page">
    <header className="page-header"><div><h1>Usuarios</h1><p>Busca jugadores, revisa su estado y aplica sanciones.</p></div></header>
    <div className="toolbar">
      <input type="search" aria-label="Buscar por correo o DNI" placeholder="Buscar por correo o DNI" value={search} onChange={(e) => setSearch(e.target.value)} />
      <select aria-label="Filtrar por estado" value={filters.status} onChange={(e) => change('status', e.target.value)}>
        <option value="">Todos los estados</option><option value="active">Activos</option><option value="suspended">Suspendidos</option></select>
      <select aria-label="Filtrar por rol" value={filters.role} onChange={(e) => change('role', e.target.value)}>
        <option value="">Todos los roles</option><option value="user">Jugadores</option><option value="admin">Administradores</option></select>
      {loading && <span className="inline-loading" role="status"><span className="spinner" aria-hidden="true" />Buscando…</span>}
    </div>
    {error && <p role="alert">{error}</p>}
    {!data && !error && <Loading />}
    {data && <div className="table-card"><table>
      <thead><tr>{COLUMNS.map(([field, label]) => <th key={label}>{field ? <button type="button" className="sort" onClick={() => sortBy(field)}>{label}{arrow(field)}</button> : label}</th>)}<th>Acciones</th></tr></thead>
      <tbody>{data.results.length === 0 ? <tr><td colSpan={8} className="empty">No hay usuarios que coincidan.</td></tr>
        : data.results.map((user) => <tr key={user.id}>
          <td>{user.id}</td><td>{user.email}{user.nickname && <small className="muted"> · {user.nickname}</small>}</td><td>{user.dni}</td>
          <td><span className={`badge ${user.role === 'ADMIN' ? 'badge-blue' : 'badge-gray'}`}>{user.role === 'ADMIN' ? 'Admin' : 'Jugador'}</span></td>
          <td><span className={`badge ${user.status === 'ACTIVE' ? 'badge-green' : 'badge-red'}`}>{user.status === 'ACTIVE' ? 'Activo' : 'Suspendido'}</span></td>
          <td>{credits(user.balance)}</td><td>{date(user.created_at)}</td>
          <td><button type="button" className={user.status === 'ACTIVE' ? 'danger small' : 'secondary small'} onClick={() => setTarget(user)}>
            {user.status === 'ACTIVE' ? 'Suspender' : 'Activar'}</button></td>
        </tr>)}</tbody></table>
      <Pagination page={page} count={data.count} onChange={(value) => { setLoading(true); setPage(value); }} disabled={loading} />
    </div>}
    {target && <SanctionModal user={target} onClose={() => setTarget(null)} onDone={() => { setTarget(null); setVersion((v) => v + 1); }} />}
  </section>;
}
