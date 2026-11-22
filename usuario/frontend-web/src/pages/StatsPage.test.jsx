import { it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import StatsPage from './StatsPage';
import { getStatsSummary } from '../services/historyService';
const session = { user: { id: 1 } };
vi.mock('../hooks/useUser', () => ({ default: () => session }));
vi.mock('../services/historyService', () => ({ getStatsSummary: vi.fn() }));
vi.mock('../services/statsService', () => ({
  getWinRate: vi.fn().mockResolvedValue({ won: 1, lost: 1, push: 0, total: 2, win_rate: 50 }),
  getBiggestWin: vi.fn().mockResolvedValue({ record: null }),
  getBalanceHistory: vi.fn().mockResolvedValue({ opening_balance: '0.00', closing_balance: '0.00', points: [] }),
}));
it('HU40 distingue pago de neto y consulta el rango seleccionado', async () => {
  getStatsSummary.mockResolvedValue({ total_wagered: '100.00', total_paid: '80.00', net: '-20.00' });
  const user = userEvent.setup(); render(<StatsPage />);
  expect(await screen.findByText('-20.00 CRD')).toBeInTheDocument();
  expect(screen.getByText('80.00 CRD')).toBeInTheDocument();
  await user.type(screen.getByLabelText('Desde'), '2026-10-19');
  await user.click(screen.getByRole('button', { name: 'Aplicar fechas' }));
  expect(getStatsSummary).toHaveBeenLastCalledWith({ startDate: '2026-10-19' });
});
