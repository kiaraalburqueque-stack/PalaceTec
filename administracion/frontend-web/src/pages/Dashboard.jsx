import { useCallback, useEffect, useState } from 'react';
import { getFinancialFlow, getKpis } from '../services/adminService';
import KpiCard from '../components/KpiCard';
import FinancialChart from '../components/FinancialChart';
import Loading from '../components/Loading';
import { credits, dateTime } from '../utils/format';

// HU47 y HU48: indicadores del día y flujo financiero del último mes.
export default function Dashboard() {
  const [kpis, setKpis] = useState(null);
  const [flow, setFlow] = useState(null);
  const [error, setError] = useState('');
  const load = useCallback(() => Promise.all([getKpis(), getFinancialFlow(30)])
    .then(([k, f]) => { setKpis(k); setFlow(f); setError(''); }).catch((err) => setError(err.message)), []);
  useEffect(() => {
    void load();
    const timer = setInterval(load, 60000);
    return () => clearInterval(timer);
  }, [load]);
  return <section className="page">
    <header className="page-header"><div><h1>Dashboard</h1><p>Actividad del casino de hoy ({kpis?.date || '…'}) comparada con ayer.</p></div>
      {kpis && <small className="muted">Actualizado {dateTime(kpis.generated_at)}{kpis.cached ? ' · caché 60 s' : ''}</small>}</header>
    {error && <p role="alert">{error}</p>}
    {!kpis && !error && <Loading />}
    {kpis && <div className="kpi-grid">
      <KpiCard label="Usuarios en línea" value={kpis.online_users} hint="Sesiones activas y jugadores en mesa" />
      <KpiCard label="Créditos apostados hoy" value={credits(kpis.wagered_today)} change={kpis.wagered_change_pct} previous={credits(kpis.wagered_yesterday)} />
      <KpiCard label="Créditos pagados hoy" value={credits(kpis.paid_today)} change={kpis.paid_change_pct} previous={credits(kpis.paid_yesterday)} invert />
      <KpiCard label="Ganancia simulada" value={credits(kpis.profit_today)} change={kpis.profit_change_pct} previous={credits(kpis.profit_yesterday)} />
    </div>}
    <FinancialChart data={flow} />
  </section>;
}
