export default function RoundResult({ result }) {
  if (!result) return null;
  return <section className="prebet-balance" aria-label="Resultado confirmado">
    <h2>Resultado de la ronda {result.round_id}</h2>
    <p>Número ganador: <strong>{result.winning_number}</strong> · {result.color} · {result.parity ?? 'Sin paridad'}</p>
    <small>Resultado confirmado por el servidor. Pago total incluye la devolución de la apuesta.</small>
  </section>;
}
