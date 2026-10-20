import { apiRequest, endSession } from './api';
export async function getCsrfToken() { return (await apiRequest('/api/v1/auth/csrf')).csrfToken; }
export const register = (data) => apiRequest('/api/v1/auth/register', { method: 'POST', body: JSON.stringify(data) });
export const logout = async () => { const data = await apiRequest('/api/v1/auth/logout', { method: 'POST', body: '{}' }); endSession(); return data; };
export const login = (email, password) => apiRequest('/api/v1/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
export const getCurrentUser = () => apiRequest('/api/v1/auth/me');
export const getUserProfile = () => apiRequest('/api/v1/users/me');
export const updateProfile = (data) => apiRequest('/api/v1/users/profile', { method: 'PUT', body: JSON.stringify(data) });
export const changePassword = (data) => apiRequest('/api/v1/users/change-password', { method: 'PUT', body: JSON.stringify(data) });
