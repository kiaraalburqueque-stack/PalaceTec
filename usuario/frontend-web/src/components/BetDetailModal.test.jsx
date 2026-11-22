import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import BetDetailModal, { betTarget } from './BetDetailModal';
import { getBetDetail } from '../services/historyService';
vi.mock('../services/historyService', () => ({ getBetDetail: vi.fn() }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });
const bet = { id: 5, kind: 'COLOR', selection: 'rojo', chips: [10, 50], amount: '60.00', payout: '120.00', net: '60.00', status: 'WON',
  game: 'Ruleta Europea', room_name: 'Mesa VIP', created_at: '2026-11-10T15:00:00Z', round: { id: 9, winning_number: 3, winning_color: 'rojo' } };
it('HU54 muestra el tapete con las fichas apostadas y el resultado', async () => {
  getBetDetail.mockResolvedValue(bet);
  render(<BetDetailModal betId={5} onClose={() => {}} />);
  expect(await screen.findByRole('img', { name: 'Tapete con 2 fichas en Color rojo' })).toBeInTheDocument();
  expect(screen.getByText('10 + 50 = 60.00 CRD')).toBeInTheDocument();
  expect(screen.getByText('Ganada')).toBeInTheDocument();
  expect(getBetDetail).toHaveBeenCalledWith(5);
});
it('HU54 ubica cada tipo de apuesta en su casilla del tapete', () => {
  expect(betTarget({ kind: 'STRAIGHT_UP', selection: '0' }).h).toBeGreaterThan(betTarget({ kind: 'STRAIGHT_UP', selection: '1' }).h);
  expect(betTarget({ kind: 'DOZEN', selection: '2' }).label).toBe('2ª docena');
  expect(betTarget({ kind: 'PARITY', selection: 'impar' }).label).toBe('Impar');
});
it('HU54 informa si la apuesta no pertenece al jugador', async () => {
  getBetDetail.mockRejectedValue(new Error('No encontrado.'));
  const onClose = vi.fn();
  const user = userEvent.setup();
  render(<BetDetailModal betId={99} onClose={onClose} />);
  expect(await screen.findByRole('alert')).toHaveTextContent('No encontrado.');
  await user.click(screen.getByRole('button', { name: 'Cerrar' }));
  expect(onClose).toHaveBeenCalled();
});
