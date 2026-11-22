export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000').replace(/\/$/, '');
let renewal;
export function endSession() {
  window.dispatchEvent(new Event('palacetec:session-ended'));
}
export function refreshSession() {
  if (!renewal) {
    renewal = apiRequest('/api/v1/auth/refresh', { method: 'POST', body: '{}' }, false)
      .then((data) => { window.dispatchEvent(new Event('palacetec:session-renewed')); return data; })
      .catch((error) => { endSession(); throw error; })
      .finally(() => { renewal = null; });
  }
  return renewal;
}
export async function apiRequest(path, options = {}, retry = true) {
  const headers = { ...options.headers };
  if (options.method && !['GET', 'HEAD'].includes(options.method)) {
    const csrf = await apiRequest('/api/v1/auth/csrf', {}, false);
    headers['X-CSRFToken'] = csrf.csrfToken;
    headers['Content-Type'] = 'application/json';
  }
  const response = await fetch(API_BASE_URL + path, { ...options, headers, credentials: 'include' });
  const data = await response.json().catch(() => ({}));
  const protectedPath = !path.startsWith('/api/v1/auth/') || path === '/api/v1/auth/me';
  if (response.status === 401 && retry && protectedPath) {
    await refreshSession();
    return apiRequest(path, options, false);
  }
  if (response.status === 401 && protectedPath) endSession();
  if (!response.ok) {
    const message = typeof data.detail === 'string' ? data.detail : Object.entries(data).map(([field, value]) => field + ': ' + (Array.isArray(value) ? value.join(' ') : value)).join(' ');
    const error = new Error(message || 'No se pudo completar la solicitud.');
    error.status = response.status; error.code = data.code; error.data = data;
    throw error;
  }
  return data;
}
// HU66: descarga de archivos (PDF o Excel) con la misma sesión, renovación y manejo de errores.
export async function apiDownload(path, retry = true) {
  const response = await fetch(API_BASE_URL + path, { credentials: 'include' });
  if (response.status === 401 && retry) {
    await refreshSession();
    return apiDownload(path, false);
  }
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    const message = typeof data.detail === 'string' ? data.detail : Object.values(data).flat().join(' ');
    const error = new Error(message || 'No se pudo descargar el archivo.');
    error.status = response.status; error.data = data;
    throw error;
  }
  return response.blob();
}
