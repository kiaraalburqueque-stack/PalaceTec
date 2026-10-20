import { useState } from 'react';
export default function DateRangeFilter({ onApply, disabled = false }) {
  const [startDate, setStart] = useState('');
  const [endDate, setEnd] = useState('');
  const [error, setError] = useState('');
  return <form className="date-filter" onSubmit={(event) => {
    event.preventDefault();
    if (startDate && endDate && startDate > endDate) { setError('La fecha inicial no puede ser posterior a la final.'); return; }
    setError(''); onApply({ ...(startDate && { startDate }), ...(endDate && { endDate }) });
  }}><label>Desde<input type="date" value={startDate} onChange={(e) => setStart(e.target.value)} /></label>
    <label>Hasta<input type="date" value={endDate} onChange={(e) => setEnd(e.target.value)} /></label>
    <p>Fechas de Lima. Se incluye el día final completo.</p>
    <button disabled={disabled}>Aplicar fechas</button>
    <button type="button" disabled={disabled} onClick={() => { setStart(''); setEnd(''); setError(''); onApply({}); }}>Quitar filtros</button>
    {error && <p role="alert">{error}</p>}
  </form>;
}
