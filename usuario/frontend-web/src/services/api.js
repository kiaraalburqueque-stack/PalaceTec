export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000').replace(/\/$/, '');
export async function apiRequest(path, options = {}) {
  const headers = { ...options.headers };
  if (options.method && !['GET', 'HEAD'].includes(options.method)) {
    const csrf = await apiRequest('/api/v1/auth/csrf');
    headers['X-CSRFToken'] = csrf.csrfToken;
    headers['Content-Type'] = 'application/json';
  }
  const response = await fetch(API_BASE_URL + path, { ...options, headers, credentials: 'include' });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const message = typeof data.detail === 'string' ? data.detail : Object.entries(data).map(([field, value]) => field + ': ' + (Array.isArray(value) ? value.join(' ') : value)).join(' ');
    const error = new Error(message || 'No se pudo completar la solicitud.');
    error.status = response.status; error.code = data.code; error.data = data;
    throw error;
  }
  return data;
}
