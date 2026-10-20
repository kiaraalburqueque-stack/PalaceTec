import { apiRequest } from './api';
export const getBetHistory = (params = {}) => apiRequest('/api/v1/users/bets-history?' + new URLSearchParams(params));
export const getStatsSummary = (params = {}) => apiRequest('/api/v1/stats/summary?' + new URLSearchParams(params));
