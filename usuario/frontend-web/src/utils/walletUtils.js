
export function formatCredits(amount) {
  const safeAmount =
    Number.isFinite(amount) && amount >= 0 ? amount : 0;

  return `${new Intl.NumberFormat('en-US', {
    maximumFractionDigits: 0,
  }).format(safeAmount)} CRD`;
}

export function calculateAvailableBalance(
  bonusBalance,
  earnedBalance
) {
  const bonus = Math.max(0, Number(bonusBalance) || 0);
  const earned = Math.max(0, Number(earnedBalance) || 0);

  return bonus + earned;
}
