// HU69: tarjeta con la probabilidad, el pago y la ventaja de la casa calculados por el backend.
export default function OddsCard({ odds }) {
  return <dl className="odds-card" aria-label={`Probabilidades de ${odds.label}`}>
    <div className="odds-title">{odds.label}</div>
    <div><dt>Probabilidad</dt><dd>{odds.probability} <small>({odds.probability_pct} %)</small></dd></div>
    <div><dt>Pago</dt><dd>{odds.payout.replace(':', ' a ')}</dd></div>
    <div><dt>Ventaja de la casa</dt><dd>{odds.house_edge_pct} %</dd></div>
  </dl>;
}
