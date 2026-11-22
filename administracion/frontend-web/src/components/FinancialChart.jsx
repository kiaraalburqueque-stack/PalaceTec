import { useState } from 'react';
import { credits } from '../utils/format';

// HU48: barras dobles por día (apostado y pagado) y margen neto al pasar el cursor.
const W = 900; const H = 300; const P = { l: 70, r: 16, t: 16, b: 40 };

export function chartScale(days) {
  const max = Math.max(1, ...days.flatMap((day) => [Number(day.wagered), Number(day.paid)]));
  const nice = 10 ** Math.floor(Math.log10(max));
  const top = Math.ceil(max / nice) * nice;
  return { top, y: (value) => P.t + (1 - Number(value) / top) * (H - P.t - P.b), slot: (W - P.l - P.r) / Math.max(1, days.length) };
}

export default function FinancialChart({ data }) {
  const [hover, setHover] = useState(null);
  if (!data) return null;
  const { days, totals } = data;
  const { top, y, slot } = chartScale(days);
  const bar = Math.max(2, slot * 0.36);
  const empty = days.every((day) => Number(day.wagered) === 0 && Number(day.paid) === 0);
  const point = hover != null ? days[hover] : null;
  return <section className="card chart-card">
    <header className="chart-header"><div><h2>Créditos apostados vs. pagados</h2><p className="muted">Últimos {days.length} días · hora de Lima</p></div>
      <ul className="legend"><li><i className="wagered" />Apostado</li><li><i className="paid" />Pagado</li></ul></header>
    {empty && <p className="muted">Todavía no hay apuestas en este período.</p>}
    <div className="chart-wrap">
      <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={`Apostado ${totals.wagered}, pagado ${totals.paid}, margen ${totals.net_margin} CRD`} onMouseLeave={() => setHover(null)}>
        {[0, 0.25, 0.5, 0.75, 1].map((ratio) => <g key={ratio}><line x1={P.l} x2={W - P.r} y1={y(top * ratio)} y2={y(top * ratio)} className="grid" />
          <text x={P.l - 8} y={y(top * ratio) + 4} textAnchor="end" className="axis">{Math.round(top * ratio).toLocaleString('es-PE')}</text></g>)}
        {days.map((day, index) => { const x = P.l + index * slot + slot / 2;
          return <g key={day.date} onMouseEnter={() => setHover(index)}>
            <rect x={x - slot / 2} y={P.t} width={slot} height={H - P.t - P.b} className={hover === index ? 'hover-band' : 'band'} />
            <rect x={x - bar} y={y(day.wagered)} width={bar} height={H - P.b - y(day.wagered)} className="bar wagered" />
            <rect x={x} y={y(day.paid)} width={bar} height={H - P.b - y(day.paid)} className="bar paid" />
            {(index % Math.ceil(days.length / 10) === 0 || index === days.length - 1) && <text x={x} y={H - 14} textAnchor="middle" className="axis">{day.date.slice(8)}/{day.date.slice(5, 7)}</text>}
          </g>; })}
      </svg>
      {point && <div className="chart-tooltip" role="status" style={{ left: `${((P.l + hover * slot + slot / 2) / W) * 100}%` }}>
        <strong>{new Date(point.date + 'T12:00:00').toLocaleDateString('es-PE', { weekday: 'short', day: '2-digit', month: 'short' })}</strong>
        <span>Apostado: {credits(point.wagered)}</span><span>Pagado: {credits(point.paid)}</span>
        <span className={Number(point.net_margin) >= 0 ? 'good' : 'bad'}>Margen neto: {credits(point.net_margin)}</span></div>}
    </div>
    <dl className="chart-totals"><div><dt>Total apostado</dt><dd>{credits(totals.wagered)}</dd></div><div><dt>Total pagado</dt><dd>{credits(totals.paid)}</dd></div>
      <div><dt>Margen neto</dt><dd className={Number(totals.net_margin) >= 0 ? 'good' : 'bad'}>{credits(totals.net_margin)}</dd></div></dl>
  </section>;
}
