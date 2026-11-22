import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import TransactionAudit from './TransactionAudit';
import { getTransactions } from '../services/adminService';
vi.mock('../services/adminService', () => ({ getTransactions: vi.fn(), transactionsCsvUrl: (params) => `/export?type=${params.type || ''}` }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });
it('HU49 busca por usuario, filtra por tipo y exporta lo filtrado', async () => {
  getTransactions.mockResolvedValue({ count: 1, results: [{ id: 9, created_at: '2026-11-10T12:00:00Z', user_id: 2, email: 'k@palacetec.pe', type: 'WIN', type_label: 'Premio',
    amount: '40.00', balance_after: '960.00', reference: 'roulette:win:2', bet: { source: 'roulette', bet_id: 2, round_id: 7 } }] });
  const user = userEvent.setup();
  render(<TransactionAudit />);
  expect(await screen.findByText('Apuesta #2 · ronda 7')).toBeInTheDocument();
  await user.type(screen.getByLabelText('ID de usuario'), '2a');
  await user.click(screen.getByRole('button', { name: 'Buscar' }));
  expect(getTransactions).toHaveBeenLastCalledWith(expect.objectContaining({ user_id: '2', page: 1 }));
  await user.selectOptions(screen.getByLabelText('Tipo de evento'), 'WIN');
  expect(getTransactions).toHaveBeenLastCalledWith(expect.objectContaining({ type: 'WIN' }));
  expect(screen.getByRole('link', { name: /Exportar CSV/ })).toHaveAttribute('href', '/export?type=WIN');
});
