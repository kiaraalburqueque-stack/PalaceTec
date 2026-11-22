import { useEffect, useState } from 'react';
import { getBalanceHistory } from '../services/statsService';

// HU53: evolución del saldo por día (7 o 30 días), con detalle al pasar el cursor.
const WIDTH = 640; const HEIGHT = 240; const PAD = { left: 64, right: 16, top: 16, bottom: 34 };

export function chartPoints(points) {
  const values = points.map((point) => Number(point.balance));
  let min = Math.min(...values); let max = Math.max(...values);
  if (min === max) { min = Math.max(0, min - 50); max += 50; }
  const x = (index) => PAD.left + (points.length === 1 ? 0 : (index * (WIDTH - PAD.left - PAD.right)) / (points.length - 1));
  const y = (value) => PAD.top + ((max - value) * (HEIGHT - PAD.top - PAD.bottom)) / (max - min);
  return { min, max, coords: points.map((point, index) => ({ ...point, x: x(index), y: y(Number(point.balance)) })) };
}

const shortDate = (iso) => { const [, month, day] = iso.split('-'); return `${day}/${month}`; };

export default function BalanceChart() {
  const [period, setPeriod] = useState(7);
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [hover, setHover] = useState(null);
  useEffect(() => {
    let active = true;
    getBalanceHistory(period).then((value) => { if (active) { setData(value); setError(''); } })
      .catch((err) => { if (active) setError(err.message); });
    return () => { active = false; };
  }, [period]);
  const chart = data?.points?.length ? chartPoints(data.points) : null;
  const path = chart?.coords.map((point, index) => `${index ? 'L' : 'M'}${point.x.toFixed(1)} ${point.y.toFixed(1)}`).join(' ');
  const step = period === 30 ? 5 : 1;
  const point = hover != null && chart ? chart.coords[hover] : null;
  return <section className="stats-card balance-card">
    <div className="stats-card-header"><h2>Evolución del saldo</h2>
      <div role="group" aria-label="Período" className="segmented">
        {[7, 30].map((value) => <button key={value} type="button" aria-pressed={period === value} onClick={() => { setHover(null); setPeriod(value); }}>{value} días</button>)}
      </div></div>
    {error && <p role="alert">{error}</p>}
    {!data && !error && <p role="status">Consultando saldo...</p>}
    {chart && <div className="balance-chart">
      <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} role="img" aria-label={`Saldo de los últimos ${period} días: de ${data.opening_balance} a ${data.closing_balance} CRD`}
        onMouseLeave={() => setHover(null)}>
        {[0, 0.5, 1].map((ratio) => { const value = chart.max - (chart.max - chart.min) * ratio; const y = PAD.top + ratio * (HEIGHT - PAD.top - PAD.bottom);
          return <g key={ratio}><line x1={PAD.left} x2={WIDTH - PAD.right} y1={y} y2={y} className="grid-line" /><text x={PAD.left - 8} y={y + 4} textAnchor="end" className="axis-label">{Math.round(value)}</text></g>; })}
        {chart.coords.map((p, index) => index % step === 0 || index === chart.coords.length - 1
          ? <text key={p.date} x={p.x} y={HEIGHT - 10} textAnchor="middle" className="axis-label">{shortDate(p.date)}</text> : null)}
        <path d={`${path} L${chart.coords.at(-1).x} ${HEIGHT - PAD.bottom} L${chart.coords[0].x} ${HEIGHT - PAD.bottom} Z`} className="balance-area" />
        <path d={path} className="balance-line" />
        {chart.coords.map((p, index) => <g key={p.date}>
          <rect x={p.x - (WIDTH / chart.coords.length) / 2} y={PAD.top} width={WIDTH / chart.coords.length} height={HEIGHT - PAD.top - PAD.bottom}
            fill="transparent" onMouseEnter={() => setHover(index)} />
          <circle cx={p.x} cy={p.y} r={hover === index ? 6 : 3.5} className="balance-dot" tabIndex={0} onFocus={() => setHover(index)}
            aria-label={`${shortDate(p.date)}: ${p.balance} CRD`} />
        </g>)}
      </svg>
      {point && <div className="chart-tooltip" role="status" style={{ left: `${(point.x / WIDTH) * 100}%`, top: `${(point.y / HEIGHT) * 100}%` }}>
        <strong>{point.balance} CRD</strong><span>{new Date(point.date + 'T12:00:00').toLocaleDateString('es-PE', { weekday: 'short', day: '2-digit', month: 'short' })}</span>
        <small>{point.movements} movimiento{point.movements === 1 ? '' : 's'}</small></div>}
    </div>}
    {data && <p className="chart-footnote">Saldo inicial del período: {data.opening_balance} CRD · Saldo actual: {data.closing_balance} CRD · Hora de Lima.</p>}
  </section>;
}
