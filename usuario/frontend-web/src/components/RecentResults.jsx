export default function RecentResults({ results = [] }) {
  const recent = results.filter((item, index) => results.findIndex((other) => other.round_id === item.round_id) === index).slice(0, 10);
  return <section className="prebet-balance recent-results"><h2>Últimos diez resultados</h2>
    {recent.length ? <ol aria-label="Resultados recientes">{recent.map((item) => <li key={item.round_id} className={`result-${item.color}`}>Ronda {item.round_id}: <strong>{item.number}</strong> · {item.color}</li>)}</ol> : <p>Aún no hay rondas liquidadas en esta sala.</p>}
    <small>Más reciente primero. Los resultados anteriores no predicen el siguiente giro.</small>
  </section>;
}
