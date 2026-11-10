import { it, expect, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import BigWinCard from './BigWinCard';
afterEach(cleanup);
it('HU52 destaca la mayor ganancia y se actualiza con un récord mayor', () => {
  const record = { net: '10.00', payout: '20.00', amount: '10.00', game: 'Ruleta Europea', round_id: 4, created_at: '2026-11-10T15:00:00Z' };
  const { rerender } = render(<BigWinCard record={record} />);
  expect(screen.getByText(/\+10\.00 CRD/)).toBeInTheDocument();
  rerender(<BigWinCard record={{ ...record, net: '350.00', payout: '360.00', round_id: 9 }} />);
  expect(screen.getByText(/\+350\.00 CRD/)).toBeInTheDocument();
  expect(screen.getByText(/Ronda 9/)).toBeInTheDocument();
});
it('HU52 invita a jugar cuando no hay récord', () => {
  render(<BigWinCard record={null} />);
  expect(screen.getByText(/Todavía no registras una victoria/)).toBeInTheDocument();
});
