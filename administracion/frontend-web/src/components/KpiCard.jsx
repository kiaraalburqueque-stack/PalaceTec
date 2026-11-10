import { percent } from '../utils/format';

// HU47: tarjeta KPI con flecha de subida o bajada frente al día anterior.
export default function KpiCard({ label, value, change, previous, invert = false, hint }) {
  const direction = change == null || change === 0 ? 'flat' : change > 0 ? 'up' : 'down';
  const good = direction === 'flat' ? 'neutral' : (direction === 'up') !== invert ? 'good' : 'bad';
  return <article className="kpi-card">
    <span className="kpi-label">{label}</span>
    <strong className="kpi-value">{value}</strong>
    {change !== undefined && <span className={`kpi-change ${good}`} aria-label={`Variación frente a ayer: ${percent(change)}`}>
      {direction === 'up' ? '▲' : direction === 'down' ? '▼' : '•'} {percent(change)}</span>}
    {previous && <small>Ayer: {previous}</small>}
    {hint && <small>{hint}</small>}
  </article>;
}
