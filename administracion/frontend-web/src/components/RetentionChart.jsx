import { useState } from 'react';

// HU72: línea de usuarios activos por día (DAU) de los últimos 30 días con el índice DAU/MAU destacado.
const W = 900; const H = 260; const P = { l: 46, r: 16, t: 16, b: 36 };

export default function RetentionChart({ data }) {
  const [hover, setHover] = useState(null);
  if (!data) return null;
  const { days } = data;
  const top = Math.max(1, ...days.map((day) => day.dau));
  const step = (W - P.l - P.r) / Math.max(1, days.length - 1);
  const x = (index) => P.l + index * step;
  const y = (value) => P.t + (1 - value / top) * (H - P.t - P.b);
  const path = days.map((day, index) => `${index ? 'L' : 'M'}${x(index).toFixed(1)},${y(day.dau).toFixed(1)}`).join(' ');
  const ticks = [...new Set([0, Math.round(top / 2), top])];
  const point = hover != null ? days[hover] : null;
  return <section className="card chart-card retention-card" aria-labelledby="retention-title">
    <header className="chart-header"><div><h2 id="retention-title">Retención de jugadores</h2>
      <p className="muted">Usuarios activos por día (jugaron al menos una ronda) · {data.from} a {data.to}</p></div>
      <div className="retention-index" aria-label={`Índice DAU/MAU ${data.dau_mau_ratio}%`}>
        <strong>{data.dau_mau_ratio}%</strong><span>DAU/MAU</span></div></header>
    {data.mau === 0 && <p className="muted">Todavía no hay jugadores activos en este período.</p>}
    <div className="chart-wrap">
      <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={`DAU promedio ${data.average_dau}, MAU ${data.mau}`} onMouseLeave={() => setHover(null)}>
        {ticks.map((tick) => <g key={tick}><line x1={P.l} x2={W - P.r} y1={y(tick)} y2={y(tick)} className="grid" />
          <text x={P.l - 8} y={y(tick) + 4} textAnchor="end" className="axis">{tick}</text></g>)}
        <path d={path} className="retention-line" />
        {days.map((day, index) => <g key={day.date} onMouseEnter={() => setHover(index)}>
          <rect x={x(index) - step / 2} y={P.t} width={step} height={H - P.t - P.b} className="band" />
          <circle cx={x(index)} cy={y(day.dau)} r={hover === index ? 5 : 3} className="retention-dot" />
          {(index % 5 === 0 || index === days.length - 1) && <text x={x(index)} y={H - 12} textAnchor="middle" className="axis">{day.date.slice(8)}/{day.date.slice(5, 7)}</text>}
        </g>)}
      </svg>
      {point && <div className="chart-tooltip" role="status" style={{ left: `${(x(hover) / W) * 100}%` }}>
        <strong>{point.date}</strong><span>{point.dau} {point.dau === 1 ? 'usuario activo' : 'usuarios activos'}</span></div>}
    </div>
    <dl className="chart-totals"><div><dt>Activos hoy (DAU)</dt><dd>{data.today_dau}</dd></div>
      <div><dt>DAU promedio</dt><dd>{data.average_dau}</dd></div><div><dt>Activos en 30 días (MAU)</dt><dd>{data.mau}</dd></div></dl>
  </section>;
}
