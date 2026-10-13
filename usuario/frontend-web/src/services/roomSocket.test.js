import { afterEach, describe, expect, it, vi } from 'vitest';
import { connectRoom } from './roomSocket';
afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers(); });
describe('HU10 HU16 conexión en vivo', () => {
  it('sincroniza, entrega eventos y cancela reconexiones al salir', () => {
    vi.useFakeTimers();
    const instances = [];
    class Socket {
      constructor(url) { this.url = url; this.send = vi.fn(); this.close = vi.fn(); instances.push(this); }
    }
    vi.stubGlobal('WebSocket', Socket);
    const event = vi.fn(); const status = vi.fn();
    const stop = connectRoom(2, event, status);
    expect(instances[0].url).toContain('/ws/roulette/2/');
    instances[0].onopen();
    expect(instances[0].send).toHaveBeenCalledWith(JSON.stringify({ event: 'room:sync' }));
    instances[0].onmessage({ data: JSON.stringify({ event: 'wallet:update', data: { balance: '100.00' } }) });
    expect(event).toHaveBeenCalledWith({ event: 'wallet:update', data: { balance: '100.00' } });
    instances[0].onclose({ code: 1006 });
    vi.advanceTimersByTime(2000);
    expect(instances).toHaveLength(2);
    stop(); vi.advanceTimersByTime(10000);
    expect(instances).toHaveLength(2);
    expect(instances[1].close).toHaveBeenCalled();
  });
  it('no reconecta indefinidamente una sesión vencida', () => {
    vi.useFakeTimers(); let socket;
    vi.stubGlobal('WebSocket', class { constructor() { socket = this; } close() {} });
    const status = vi.fn(); const stop = connectRoom(1, vi.fn(), status);
    socket.onclose({ code: 4401 });
    expect(status).toHaveBeenLastCalledWith('Sesión vencida o sala no disponible');
    expect(vi.getTimerCount()).toBe(0); stop();
  });
});
