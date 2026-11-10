import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import BalanceChart, { chartPoints } from './BalanceChart';
import { getBalanceHistory } from '../services/statsService';
vi.mock('../services/statsService', () => ({ getBalanceHistory: vi.fn() }));
afterEach(cleanup);
const points = (days) => Array.from({ length: days }, (_, index) => ({ date: `2026-11-${String(index + 1).padStart(2, '0')}`, balance: String(1000 + index * 10), movements: 1 }));
it('HU53 ordena la serie por fecha de izquierda a derecha', () => {
  const { coords } = chartPoints(points(7));
  expect(coords.map((point) => point.x)).toEqual([...coords.map((point) => point.x)].sort((a, b) => a - b));
  expect(coords[0].y).toBeGreaterThan(coords[6].y);
});
it('HU53 cambia entre 7 y 30 días mostrando el saldo inicial', async () => {
  getBalanceHistory.mockImplementation((period) => Promise.resolve({ opening_balance: '990.00', closing_balance: '1060.00', points: points(period === 30 ? 30 : 7) }));
  const user = userEvent.setup();
  render(<BalanceChart />);
  expect(await screen.findByText(/Saldo inicial del período: 990.00 CRD/)).toBeInTheDocument();
  expect(getBalanceHistory).toHaveBeenLastCalledWith(7);
  await user.click(screen.getByRole('button', { name: '30 días' }));
  expect(getBalanceHistory).toHaveBeenLastCalledWith(30);
  expect(await screen.findByLabelText('30/11: 1290 CRD')).toBeInTheDocument();
});
