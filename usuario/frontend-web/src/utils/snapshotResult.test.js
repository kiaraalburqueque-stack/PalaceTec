import { it, expect } from 'vitest';
import { snapshotResult } from './snapshotResult';
it('HU23 recupera el último resultado aunque ya exista otra ronda abierta', () => {
  expect(snapshotResult({ round: { id: 9, status: 'OPEN' }, recent_results: [{ round_id: 8, number: 0, color: 'verde' }] })).toEqual({ round_id: 8, winning_number: 0, color: 'verde', parity: null });
  expect(snapshotResult({ recent_results: [] })).toBeNull();
});
