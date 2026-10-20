import { describe, it, expect } from 'vitest';
import { secondsLeft } from './roundTime';
describe('HU22 cierre sincronizado', () => {
  const round = { status: 'OPEN', closes_at: '2026-10-19T12:00:15Z' };
  it('cierra exactamente al plazo y corrige el desfase', () => {
    expect(secondsLeft(round, Date.parse('2026-10-19T12:00:14Z'))).toBe(1);
    expect(secondsLeft(round, Date.parse(round.closes_at))).toBe(0);
    expect(secondsLeft(round, Date.parse('2026-10-19T12:00:10Z'), 5000)).toBe(0);
  });
  it('bloquea estados cerrados y plazos inválidos', () => {
    expect(secondsLeft({ ...round, status: 'CLOSED' }, 0)).toBe(0);
    expect(secondsLeft({ status: 'OPEN', closes_at: '?' }, 0)).toBe(0);
  });
});
