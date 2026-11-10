import { API_BASE_URL, apiRequest } from './api';

const query = (params = {}) => {
  const clean = Object.fromEntries(Object.entries(params).filter(([, value]) => value !== '' && value != null));
  const text = new URLSearchParams(clean).toString();
  return text ? `?${text}` : '';
};

// Sesión (mismo backend y cookies que el jugador)
export const login = (email, password) => apiRequest('/api/v1/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
export const logout = () => apiRequest('/api/v1/auth/logout', { method: 'POST', body: '{}' });
export const getMe = () => apiRequest('/api/v1/auth/me');

// HU41 y HU42
export const getUsers = (params) => apiRequest('/api/v1/admin/users' + query(params));
export const setUserStatus = (id, isActive, reason) => apiRequest(`/api/v1/admin/users/${id}/status`, { method: 'PATCH', body: JSON.stringify({ is_active: isActive, reason }) });
export const getSanctions = (id) => apiRequest(`/api/v1/admin/users/${id}/sanctions`);

// HU43, HU44 y HU45
export const getAdminGames = () => apiRequest('/api/v1/admin/games');
export const updateGame = (id, data) => apiRequest(`/api/v1/admin/games/${id}`, { method: 'PUT', body: JSON.stringify(data) });
export const softDeleteGame = (id) => apiRequest(`/api/v1/admin/games/${id}/soft-delete`, { method: 'PATCH', body: '{}' });
export const restoreGame = (id) => apiRequest(`/api/v1/admin/games/${id}/restore`, { method: 'PATCH', body: '{}' });

export async function createGame(formData) {
  // Multipart para enviar la portada: se arma la petición a mano (apiRequest envía JSON).
  const { csrfToken } = await apiRequest('/api/v1/auth/csrf');
  const response = await fetch(API_BASE_URL + '/api/v1/admin/games', {
    method: 'POST', body: formData, credentials: 'include', headers: { 'X-CSRFToken': csrfToken } });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(typeof data.detail === 'string' ? data.detail : 'Revisa los campos marcados.');
    error.status = response.status; error.data = data; throw error;
  }
  return data;
}

// HU46, HU47 y HU48
export const getResponsibleUsers = (params) => apiRequest('/api/v1/admin/responsible-gaming/users' + query(params));
export const getKpis = () => apiRequest('/api/v1/admin/kpis');
export const getFinancialFlow = (days = 30) => apiRequest('/api/v1/admin/reports/financial-flow?days=' + days);

// HU49 y HU50
export const getTransactions = (params) => apiRequest('/api/v1/admin/audit/transactions' + query(params));
export const transactionsCsvUrl = (params) => API_BASE_URL + '/api/v1/admin/audit/transactions/export' + query(params);
export const getAuditLogs = (params) => apiRequest('/api/v1/admin/audit-logs' + query(params));

// Promociones (respetan las preferencias de HU58)
export const sendPromotion = (data) => apiRequest('/api/v1/admin/promotions', { method: 'POST', body: JSON.stringify(data) });
