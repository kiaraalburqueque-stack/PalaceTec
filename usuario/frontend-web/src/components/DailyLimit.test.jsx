import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import DailyLimit from './DailyLimit';
import { getDailyLimit, saveDailyLimit } from '../services/responsibleService';
vi.mock('../services/responsibleService', () => ({ getDailyLimit: vi.fn(), saveDailyLimit: vi.fn() }));
afterEach(cleanup);
it('HU26 pide límite y presenta el aumento diferido devuelto por el servidor', async () => {
  getDailyLimit.mockResolvedValue({ configured: false });
  saveDailyLimit.mockResolvedValue({ configured: true, limit: '100.00', spent: '0.00', remaining: '100.00', pending_limit: '200.00', effective_at: '2026-10-20T12:00:00Z' });
  const user = userEvent.setup(); render(<DailyLimit />);
  expect(await screen.findByText('Configura tu límite antes de apostar.')).toBeInTheDocument();
  await user.type(screen.getByLabelText('Límite diario en CRD'), '200');
  await user.click(screen.getByRole('button', { name: 'Guardar límite' }));
  expect(saveDailyLimit).toHaveBeenCalledWith('200');
  expect(await screen.findByText(/Aumento pendiente: 200.00/)).toBeInTheDocument();
});
