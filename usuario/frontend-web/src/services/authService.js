import { API_BASE_URL, apiRequest, endSession, refreshSession } from './api';
export async function getCsrfToken() { return (await apiRequest('/api/v1/auth/csrf')).csrfToken; }
export const register = (data) => apiRequest('/api/v1/auth/register', { method: 'POST', body: JSON.stringify(data) });
export const logout = async () => { const data = await apiRequest('/api/v1/auth/logout', { method: 'POST', body: '{}' }); endSession(); return data; };
export const login = (email, password) => apiRequest('/api/v1/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
export const getCurrentUser = () => apiRequest('/api/v1/auth/me');
export const getUserProfile = () => apiRequest('/api/v1/users/me');
export const updateProfile = (data) => apiRequest('/api/v1/users/profile', { method: 'PUT', body: JSON.stringify(data) });
export const changePassword = (data) => apiRequest('/api/v1/users/change-password', { method: 'PUT', body: JSON.stringify(data) });
export const forgotPassword = (email) => apiRequest('/api/v1/auth/forgot-password', { method: 'POST', body: JSON.stringify({ email }) });
export const resetPassword = (token, newPassword) => apiRequest('/api/v1/auth/reset-password', { method: 'POST', body: JSON.stringify({ token, new_password: newPassword }) });
export const reportNotMe = (token) => apiRequest('/api/v1/auth/not-me', { method: 'POST', body: JSON.stringify({ token }) });
// HU65: subida de la foto de avatar (multipart; apiRequest solo envía JSON).
export async function uploadAvatar(file) {
  const body = new FormData();
  body.append('avatar', file);
  const send = async () => fetch(API_BASE_URL + '/api/v1/users/avatar', {
    method: 'POST', body, credentials: 'include', headers: { 'X-CSRFToken': await getCsrfToken() } });
  let response = await send();
  if (response.status === 401) { await refreshSession(); response = await send(); }
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const detail = data.avatar ? [].concat(data.avatar).join(' ') : data.detail;
    const error = new Error(detail || 'No se pudo subir la foto.');
    error.status = response.status; error.data = data; throw error;
  }
  return data;
}
