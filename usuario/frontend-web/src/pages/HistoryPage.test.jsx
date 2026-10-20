import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import '@testing-library/jest-dom/vitest';
import HistoryPage from './HistoryPage';
import { getBetHistory } from '../services/historyService';
const session = { user: { id: 1 }, loading: false };
vi.mock('../hooks/useUser', () => ({ default: () => session }));
vi.mock('../services/historyService', () => ({ getBetHistory: vi.fn() }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });
it('HU31 distingue pendientes y pagina sin aceptar URLs externas', async () => {
  getBetHistory.mockResolvedValueOnce({ count: 21, next: 'http://localhost/next', previous: null, results: [{ id: 1, created_at: '2026-10-19T12:00:00Z', game: 'Ruleta', room_id: 1, round_id: 7, kind: 'COLOR', selection: 'rojo', amount: '10.00', payout: null, net: null, status: 'PENDING' }] })
    .mockResolvedValueOnce({ count: 21, next: null, previous: 'previous', results: [] });
  const user = userEvent.setup(); render(<MemoryRouter><HistoryPage /></MemoryRouter>);
  expect(await screen.findByText('21 apuestas · Página 1')).toBeInTheDocument();
  expect(screen.getAllByText('Pendiente')).toHaveLength(3);
  await user.click(screen.getByRole('button', { name: 'Siguiente' }));
  expect(await screen.findByText('21 apuestas · Página 2')).toBeInTheDocument();
  expect(getBetHistory).toHaveBeenLastCalledWith({ page: 2, page_size: 20 });
});
it('HU32 aplica fechas en la primera página y rechaza rangos invertidos', async () => {
  getBetHistory.mockResolvedValue({ count: 0, next: null, previous: null, results: [] });
  const user = userEvent.setup(); render(<MemoryRouter><HistoryPage /></MemoryRouter>);
  await screen.findByText('0 apuestas · Página 1');
  await user.type(screen.getByLabelText('Desde'), '2026-10-20');
  await user.type(screen.getByLabelText('Hasta'), '2026-10-19');
  await user.click(screen.getByRole('button', { name: 'Aplicar fechas' }));
  expect(screen.getByRole('alert')).toHaveTextContent('fecha inicial');
  expect(getBetHistory).toHaveBeenCalledTimes(1);
  await user.clear(screen.getByLabelText('Desde'));
  await user.type(screen.getByLabelText('Desde'), '2026-10-18');
  await user.click(screen.getByRole('button', { name: 'Aplicar fechas' }));
  expect(getBetHistory).toHaveBeenLastCalledWith({ page: 1, page_size: 20, startDate: '2026-10-18', endDate: '2026-10-19' });
});
