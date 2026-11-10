import { apiRequest } from './api';
export const getWinRate = (params = {}) => apiRequest('/api/v1/stats/win-rate?' + new URLSearchParams(params));
export const getBiggestWin = () => apiRequest('/api/v1/stats/biggest-win');
export const getBalanceHistory = (period = 7) => apiRequest('/api/v1/stats/balance-history?period=' + period);
