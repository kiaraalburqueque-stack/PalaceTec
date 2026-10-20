import { apiRequest } from './api';
const base = '/api/v1/responsible-gaming';
export const getDailyLimit = () => apiRequest(`${base}/daily-limit`);
export const saveDailyLimit = (amount) => apiRequest(`${base}/daily-limit`, { method: 'POST', body: JSON.stringify({ amount }) });
export const selfExclude = (days) => apiRequest(`${base}/self-exclude`, { method: 'POST', body: JSON.stringify({ days: Number(days) }) });
export const getSessionSummary = () => apiRequest(`${base}/session-summary`);
export const getRiskAlerts = () => apiRequest(`${base}/risk-alerts`);
export const takePause = (incident_id) => apiRequest(`${base}/pause`, { method: 'POST', body: JSON.stringify({ incident_id }) });
