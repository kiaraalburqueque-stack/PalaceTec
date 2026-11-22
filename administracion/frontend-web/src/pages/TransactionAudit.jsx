import { useEffect, useState } from 'react';
import { getTransactions, transactionsCsvUrl } from '../services/adminService';
import Pagination from '../components/Pagination';
import Loading from '../components/Loading';
import { credits, dateTime } from '../utils/format';

// HU49: auditoría transaccional con búsqueda, filtro por tipo de evento y exportación CSV.
const TYPES = [['', 'Todos los eventos'], ['BET', 'Apuesta'], ['WIN', 'Premio'], ['WELCOME_BONUS', 'Bono de bienvenida']];
const empty = { user_id: '', transaction_id: '', search: '', type: '', startDate: '', endDate: '' };

export default function TransactionAudit() {
  const [form, setForm] = useState(empty);
  const [filters, setFilters] = useState(empty);
  const [page, setPage] = useState(1);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true;
    getTransactions({ ...filters, page }).then((value) => { if (active) { setData(value); setError(''); } })
      .catch((err) => { if (active) setError(err.message); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [filters, page]);
  const set = (key, value) => setForm((old) => ({ ...old, [key]: value }));
  function apply(event, next = form) { event?.preventDefault(); setLoading(true); setPage(1); setFilters(next); }
  return <section className="page">
    <header className="page-header"><div><h1>Auditoría Transaccional</h1><p>Movimientos de billetera de todos los jugadores, con su apuesta o jugada asociada.</p></div>
      <a className="secondary button-link" href={transactionsCsvUrl(filters)} download>⬇ Exportar CSV</a></header>
    <form className="toolbar filters" onSubmit={apply}>
      <input aria-label="ID de usuario" placeholder="ID usuario" inputMode="numeric" value={form.user_id} onChange={(e) => set('user_id', e.target.value.replace(/\D/g, ''))} />
      <input aria-label="ID de transacción" placeholder="ID transacción" inputMode="numeric" value={form.transaction_id} onChange={(e) => set('transaction_id', e.target.value.replace(/\D/g, ''))} />
      <input type="search" aria-label="Buscar por correo o referencia" placeholder="Correo o referencia" value={form.search} onChange={(e) => set('search', e.target.value)} />
      <select aria-label="Tipo de evento" value={form.type} onChange={(e) => { const next = { ...form, type: e.target.value }; setForm(next); apply(null, next); }}>
        {TYPES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
      <input type="date" aria-label="Desde" value={form.startDate} onChange={(e) => set('startDate', e.target.value)} />
      <input type="date" aria-label="Hasta" value={form.endDate} onChange={(e) => set('endDate', e.target.value)} />
      <button className="primary">Buscar</button>
      <button type="button" className="secondary" onClick={() => { setForm(empty); apply(null, empty); }}>Limpiar</button>
    </form>
    {error && <p role="alert">{error}</p>}
    {!data && !error && <Loading />}
    {data && <div className="table-card"><table>
      <thead><tr><th>ID</th><th>Fecha (Lima)</th><th>Usuario</th><th>Evento</th><th>Monto</th><th>Saldo posterior</th><th>Origen</th><th>Referencia</th></tr></thead>
      <tbody>{data.results.length === 0 ? <tr><td colSpan={8} className="empty">Sin movimientos para estos filtros.</td></tr>
        : data.results.map((row) => <tr key={row.id}>
          <td>{row.id}</td><td className="nowrap">{dateTime(row.created_at)}</td><td>#{row.user_id} · {row.email}</td>
          <td><span className={`badge ${row.type === 'BET' ? 'badge-red' : row.type === 'WIN' ? 'badge-green' : 'badge-blue'}`}>{row.type_label}</span></td>
          <td className={row.type === 'BET' ? 'bad' : 'good'}>{row.type === 'BET' ? '−' : '+'}{credits(row.amount)}</td>
          <td>{credits(row.balance_after)}</td>
          <td>{row.bet ? row.bet.source === 'roulette' ? `Apuesta #${row.bet.bet_id} · ronda ${row.bet.round_id}` : `${row.bet.kind} #${row.bet.play_id}` : '—'}</td>
          <td><code>{row.reference || '—'}</code></td>
        </tr>)}</tbody></table>
      <Pagination page={page} count={data.count} onChange={(value) => { setLoading(true); setPage(value); }} disabled={loading} />
    </div>}
  </section>;
}
