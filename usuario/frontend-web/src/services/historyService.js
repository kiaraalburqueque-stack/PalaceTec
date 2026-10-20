import { apiRequest } from './api';
export const getBetHistory = (params = {}) => apiRequest('/api/v1/users/bets-history?' + new URLSearchParams(params));
