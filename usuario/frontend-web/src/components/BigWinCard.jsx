// HU52: tarjeta destacada con la mayor ganancia en una sola ronda.
export default function BigWinCard({ record, loaded = true }) {
  if (!loaded) return null;
  return <section className="stats-card big-win-card" aria-label="Mayor victoria">
    <h2>Mayor victoria</h2>
    {record ? <>
      <p className="big-win-amount"><span className="sparkle" aria-hidden="true">✦</span>+{Number(record.net).toLocaleString('es-PE', { minimumFractionDigits: 2 })} CRD</p>
      <p>{record.game}{record.round_id ? ` · Ronda ${record.round_id}` : ''}</p>
      <small>Apostaste {record.amount} CRD y cobraste {record.payout} CRD el {new Date(record.created_at).toLocaleDateString('es-PE', { timeZone: 'America/Lima' })}.</small>
    </> : <p>Todavía no registras una victoria. ¡Tu récord aparecerá aquí!</p>}
  </section>;
}
