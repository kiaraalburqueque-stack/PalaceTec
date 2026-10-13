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
