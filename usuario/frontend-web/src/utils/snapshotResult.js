export function snapshotResult(snapshot) {
  const latest = snapshot.recent_results?.[0];
  if (!latest) return null;
  return { round_id: latest.round_id, winning_number: latest.number, color: latest.color,
    parity: latest.number === 0 ? null : latest.number % 2 === 0 ? 'par' : 'impar' };
}
