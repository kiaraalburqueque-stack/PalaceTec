import { useState } from 'react';
import { downloadHistory, historyFileName } from '../services/historyService';

function saveFile(blob, name) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url; link.download = name;
  document.body.appendChild(link); link.click(); link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

// HU66: botones Exportar PDF y Exportar Excel que respetan el filtro de fechas aplicado.
export default function ExportButtons({ filters }) {
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  async function run(format) {
    setBusy(format); setError('');
    try { saveFile(await downloadHistory(format, filters), historyFileName(format, filters)); }
    catch (err) { setError(err.message); }
    finally { setBusy(''); }
  }
  return <div className="export-buttons">
    <button type="button" onClick={() => run('pdf')} disabled={Boolean(busy)} aria-busy={busy === 'pdf'}>
      {busy === 'pdf' ? 'Generando PDF…' : 'Exportar PDF'}</button>
    <button type="button" onClick={() => run('xlsx')} disabled={Boolean(busy)} aria-busy={busy === 'xlsx'}>
      {busy === 'xlsx' ? 'Generando Excel…' : 'Exportar Excel'}</button>
    {error && <p role="alert">{error}</p>}
  </div>;
}
