import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiRequest, API_BASE_URL } from './api';
afterEach(() => vi.unstubAllGlobals());
describe('HTTP cookies y CSRF', () => {
  it('usa el mismo host y envía CSRF y cookies en escrituras', async () => {
    const fetch = vi.fn().mockResolvedValueOnce({ ok: true, json: async () => ({ csrfToken: 'csrf-test' }) }).mockResolvedValueOnce({ ok: true, json: async () => ({ id: 1 }) });
    vi.stubGlobal('fetch', fetch);
    expect(await apiRequest('/api/v1/auth/register', { method: 'POST', body: '{}' })).toEqual({ id: 1 });
    expect(fetch.mock.calls[0][0]).toBe(API_BASE_URL + '/api/v1/auth/csrf');
    expect(fetch.mock.calls[1][1].credentials).toBe('include');
    expect(fetch.mock.calls[1][1].headers['X-CSRFToken']).toBe('csrf-test');
  });
  it('conserva el estado y código de un error de apuesta', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 400, json: async () => ({ code: 'bets_closed', detail: 'No va más.' }) }));
    await expect(apiRequest('/api/v1/rooms/1/state')).rejects.toMatchObject({ status: 400, code: 'bets_closed', message: 'No va más.' });
  });
});
describe('HU30 renovación de sesión', () => {
  it('comparte la renovación entre solicitudes simultáneas', async () => {
    let refreshed = false;
    const fetch = vi.fn(async (url) => {
      let data = {}; let status = 200;
      if (url.endsWith('/auth/csrf')) data = { csrfToken: 'csrf' };
      else if (url.endsWith('/auth/refresh')) refreshed = true;
      else { status = refreshed ? 200 : 401; data = { restored: refreshed }; }
      return { ok: status === 200, status, json: async () => data };
    });
    vi.stubGlobal('fetch', fetch);
    const values = await Promise.all([apiRequest('/api/v1/wallet/summary'), apiRequest('/api/v1/auth/me')]);
    expect(values).toEqual([{ restored: true }, { restored: true }]);
    expect(fetch.mock.calls.filter(([url]) => url.endsWith('/auth/refresh'))).toHaveLength(1);
  });
  it('termina la sesión si falla la renovación sin repetirla', async () => {
    const ended = vi.fn(); window.addEventListener('palacetec:session-ended', ended);
    const fetch = vi.fn(async (url) => ({ ok: url.endsWith('/csrf'), status: url.endsWith('/csrf') ? 200 : 401, json: async () => url.endsWith('/csrf') ? { csrfToken: 'csrf' } : { detail: 'Sesión revocada' } }));
    vi.stubGlobal('fetch', fetch);
    await expect(apiRequest('/api/v1/users/me')).rejects.toMatchObject({ status: 401 });
    expect(ended).toHaveBeenCalledOnce();
    expect(fetch.mock.calls.filter(([url]) => url.endsWith('/auth/refresh'))).toHaveLength(1);
    window.removeEventListener('palacetec:session-ended', ended);
  });
  it('renueva una vez y repite la petición protegida', async () => {
    const reply = (status, data) => ({ ok: status === 200, status, json: async () => data });
    const fetch = vi.fn()
      .mockResolvedValueOnce(reply(401, {}))
      .mockResolvedValueOnce(reply(200, { csrfToken: 'csrf' }))
      .mockResolvedValueOnce(reply(200, {}))
      .mockResolvedValueOnce(reply(200, { balance: '100.00' }));
    vi.stubGlobal('fetch', fetch);
    expect(await apiRequest('/api/v1/wallet/summary')).toEqual({ balance: '100.00' });
    expect(fetch.mock.calls.filter(([url]) => url.endsWith('/auth/refresh'))).toHaveLength(1);
  });
  it('no renueva un error de permisos', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: false, status: 403, json: async () => ({ detail: 'Sin permiso' }) });
    vi.stubGlobal('fetch', fetch);
    await expect(apiRequest('/api/v1/admin/status')).rejects.toMatchObject({ status: 403 });
    expect(fetch).toHaveBeenCalledTimes(1);
  });
});
