import { useState } from 'react';

// HU51: gráfico circular de rondas ganadas y perdidas, sin dependencias externas.
const COLORS = { won: '#3ccf8e', lost: '#e5534b', push: '#8b96a8' };
const LABELS = { won: 'Ganadas', lost: 'Perdidas', push: 'Empates' };

export function winRateSlices(data) {
  const total = data?.total || 0;
  return ['won', 'lost', 'push']
    .filter((key) => (data?.[key] || 0) > 0)
    .map((key) => ({ key, label: LABELS[key], value: data[key], percent: total ? Math.round((data[key] * 10000) / total) / 100 : 0 }));
}

function arc(start, end, radius = 80, inner = 50) {
  const point = (angle, r) => [100 + r * Math.cos(angle - Math.PI / 2), 100 + r * Math.sin(angle - Math.PI / 2)];
  if (end - start >= Math.PI * 2 - 1e-6) end = start + Math.PI * 2 - 1e-4;
  const large = end - start > Math.PI ? 1 : 0;
  const [x1, y1] = point(start, radius); const [x2, y2] = point(end, radius);
  const [x3, y3] = point(end, inner); const [x4, y4] = point(start, inner);
  return `M${x1} ${y1} A${radius} ${radius} 0 ${large} 1 ${x2} ${y2} L${x3} ${y3} A${inner} ${inner} 0 ${large} 0 ${x4} ${y4} Z`;
}

export default function WinRateChart({ data }) {
  const [active, setActive] = useState(null);
  const slices = winRateSlices(data);
  if (!data) return null;
  if (!data.total) return <section className="stats-card"><h2>Porcentaje de victorias</h2><p>Aún no tienes apuestas liquidadas.</p></section>;
  let angle = 0;
  const hovered = slices.find((slice) => slice.key === active);
  return <section className="stats-card win-rate-card">
    <h2>Porcentaje de victorias</h2>
    <div className="win-rate-body">
      <svg viewBox="0 0 200 200" role="img" aria-label={`Win rate ${data.win_rate}%: ${data.won} ganadas y ${data.lost} perdidas de ${data.total}`}>
        {slices.map((slice) => {
          const start = angle; angle += (slice.value / data.total) * Math.PI * 2;
          return <path key={slice.key} d={arc(start, angle)} fill={COLORS[slice.key]} opacity={active && active !== slice.key ? 0.45 : 1}
            onMouseEnter={() => setActive(slice.key)} onMouseLeave={() => setActive(null)} onFocus={() => setActive(slice.key)} onBlur={() => setActive(null)}
            tabIndex={0} aria-label={`${slice.label}: ${slice.value} (${slice.percent}%)`}><title>{`${slice.label}: ${slice.value} rondas (${slice.percent}%)`}</title></path>;
        })}
        <text x="100" y="96" textAnchor="middle" className="chart-center-value">{hovered ? `${hovered.percent}%` : `${data.win_rate}%`}</text>
        <text x="100" y="118" textAnchor="middle" className="chart-center-label">{hovered ? `${hovered.label}: ${hovered.value}` : 'Win rate'}</text>
      </svg>
      <ul className="chart-legend">
        {slices.map((slice) => <li key={slice.key}><i style={{ background: COLORS[slice.key] }} />{slice.label}<strong>{slice.value}</strong><span>{slice.percent}%</span></li>)}
        <li className="legend-total">Total liquidadas<strong>{data.total}</strong></li>
      </ul>
    </div>
  </section>;
}
