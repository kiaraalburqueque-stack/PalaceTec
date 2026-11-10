import { afterEach, expect, it, vi } from 'vitest';
import { connectRoom, reconnectDelay } from './roomSocket';
vi.mock('./api', () => ({ API_BASE_URL: 'http://localhost:8000', refreshSession: vi.fn().mockResolvedValue({}), endSession: vi.fn() }));
afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers(); });
it('HU59 usa retroceso exponencial con tope de 30 segundos', () => {
  const fixed = () => 1;
  expect([0, 1, 2, 3, 10].map((attempt) => reconnectDelay(attempt, fixed))).toEqual([1000, 2000, 4000, 8000, 30000]);
});
it('HU59 se reconecta solo y pide recuperar la ronda y sus apuestas', () => {
  vi.useFakeTimers();
  const sockets = [];
  vi.stubGlobal('WebSocket', class { constructor() { this.send = vi.fn(); this.close = vi.fn(); sockets.push(this); } });
  const onEvent = vi.fn(); const status = vi.fn();
  const stop = connectRoom(3, onEvent, status);
  sockets[0].onopen();
  sockets[0].onclose({ code: 1006 });
  expect(status).toHaveBeenLastCalledWith('Reconectando');
  vi.advanceTimersByTime(1000);
  expect(sockets).toHaveLength(2);
  sockets[1].onopen();
  expect(sockets[1].send).toHaveBeenCalledWith(JSON.stringify({ event: 'room:resume' }));
  sockets[1].onmessage({ data: JSON.stringify({ event: 'RECONNECT_SUCCESS', data: { round_id: 8, bets: [{ id: 1 }], total_amount: '50.00' } }) });
  expect(onEvent).toHaveBeenLastCalledWith({ event: 'RECONNECT_SUCCESS', data: { round_id: 8, bets: [{ id: 1 }], total_amount: '50.00', reconnected: true } });
  stop();
});
it('HU42 cierra la sesión cuando el administrador suspende la cuenta', async () => {
  const { endSession } = await import('./api');
  let socket;
  vi.stubGlobal('WebSocket', class { constructor() { this.close = vi.fn(); socket = this; } });
  const stop = connectRoom(1, vi.fn(), vi.fn());
  socket.onmessage({ data: JSON.stringify({ event: 'FORCE_DISCONNECT', data: {} }) });
  expect(endSession).toHaveBeenCalled();
  expect(sessionStorage.getItem('palacetec:notice')).toMatch(/suspendida/);
  stop();
});
