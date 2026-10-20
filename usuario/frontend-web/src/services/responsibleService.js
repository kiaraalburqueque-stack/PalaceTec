import { apiRequest } from './api';
const base = '/api/v1/responsible-gaming';
export const getDailyLimit = () => apiRequest(`${base}/daily-limit`);
export const saveDailyLimit = (amount) => apiRequest(`${base}/daily-limit`, { method: 'POST', body: JSON.stringify({ amount }) });
