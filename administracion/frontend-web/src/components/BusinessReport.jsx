import { useState } from 'react';
import { downloadBusinessReport } from '../services/adminService';

const isoDate = (date) => new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10);

// HU73: descarga del informe .xlsx (KPIs, flujo diario, ranking y retención) para el rango elegido.
export default function BusinessReport() {
  const today = new Date();
  const [startDate, setStart] = useState(() => isoDate(new Date(today.getTime() - 29 * 86400000)));
  const [endDate, setEnd] = useState(() => isoDate(today));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function submit(event) {
    event.preventDefault(); setError('');
    if (startDate && endDate && startDate > endDate) { setError('La fecha inicial no puede ser posterior a la final.'); return; }
    setBusy(true);
    try {
      const blob = await downloadBusinessReport({ startDate, endDate });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url; link.download = `informe_negocio_${startDate}_${endDate}.xlsx`;
      document.body.appendChild(link); link.click(); link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }
  return <form className="card business-report" onSubmit={submit}>
    <div><h2>Informe de negocio</h2><p className="muted">Excel con KPIs, flujo diario, ranking de juegos y retención. La descarga queda en la auditoría.</p></div>
    <label>Desde<input type="date" value={startDate} onChange={(e) => setStart(e.target.value)} required /></label>
    <label>Hasta<input type="date" value={endDate} onChange={(e) => setEnd(e.target.value)} required /></label>
    <button className="primary" disabled={busy}>{busy ? 'Generando informe…' : 'Descargar informe'}</button>
    {error && <p role="alert" className="field-error">{error}</p>}
  </form>;
}
