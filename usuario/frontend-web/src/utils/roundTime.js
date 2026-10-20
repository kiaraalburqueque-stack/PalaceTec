export function secondsLeft(round, now, offset = 0) {
  if (round?.status !== 'OPEN') return 0;
  const deadline = Date.parse(round.closes_at);
  return Number.isFinite(deadline) ? Math.max(0, Math.ceil((deadline - now - offset) / 1000)) : 0;
}
