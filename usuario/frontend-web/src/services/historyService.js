import { apiDownload, apiRequest } from './api';
export const getBetHistory = (params = {}) => apiRequest('/api/v1/users/bets-history?' + new URLSearchParams(params));
export const getStatsSummary = (params = {}) => apiRequest('/api/v1/stats/summary?' + new URLSearchParams(params));
export const getBetDetail = (betId) => apiRequest(`/api/v1/users/bets/${betId}`);
// HU66: exportación del historial con el mismo filtro de fechas.
export const historyFileName = (format, filters = {}) =>
  `historial_palacetec_${filters.startDate || 'inicio'}_${filters.endDate || 'hoy'}.${format}`;
export const downloadHistory = (format, filters = {}) =>
  apiDownload('/api/v1/users/bets-history/export?' + new URLSearchParams({ format, ...filters }));
