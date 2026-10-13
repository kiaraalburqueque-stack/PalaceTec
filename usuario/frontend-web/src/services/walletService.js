import { apiRequest } from './api';
export async function getWalletBalance() {
  const data = await apiRequest('/api/v1/wallet/balance');
  const balance = Number(data.balance);
  if (!Number.isFinite(balance) || balance < 0) throw new Error('Saldo inválido.');
  return balance;
}
export const getWalletSummary = () => apiRequest('/api/v1/wallet/summary');
export const checkPreBet = (amount) => apiRequest('/api/v1/wallet/pre-bet-check', { method: 'POST', body: JSON.stringify({ amount }) });
