import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import '@testing-library/jest-dom/vitest';
import GameRulesModal from './GameRulesModal';
import { getGameRules } from '../services/roomService';
const navigate = vi.fn();
vi.mock('../services/roomService', () => ({ getGameRules: vi.fn() }));
vi.mock('react-router-dom', async (original) => ({ ...(await original()), useNavigate: () => navigate }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });
const rules = { id: 1, title: 'Ruleta Europea', rtp: '97.30', house_edge: '2.70', min_bet: '10.00', max_bet: '500.00', rules: 'Ruleta de 37 casillas.',
  payout_table: [{ bet: 'Pleno', description: 'Un número', pays: '35 a 1', probability: '2.70 %' }], play_room_id: 3 };
const open = (onClose = vi.fn()) => { getGameRules.mockResolvedValue(rules); render(<MemoryRouter><GameRulesModal gameId={1} onClose={onClose} /></MemoryRouter>); return onClose; };
it('HU60 muestra reglas, tabla de pagos y RTP', async () => {
  open();
  expect(await screen.findByText('97.30%')).toBeInTheDocument();
  expect(screen.getByRole('table', { name: 'Tabla de pagos' })).toHaveTextContent('35 a 1');
});
it('HU60 Jugar ahora lleva a la sala del juego', async () => {
  const onClose = open(); const user = userEvent.setup();
  await user.click(await screen.findByRole('button', { name: 'Jugar ahora' }));
  expect(navigate).toHaveBeenCalledWith('/ruleta/3');
  expect(onClose).toHaveBeenCalled();
});
it('HU60 se cierra con ESC y con clic fuera', async () => {
  const onClose = open();
  await screen.findByText('97.30%');
  fireEvent.keyDown(document, { key: 'Escape' });
  expect(onClose).toHaveBeenCalledTimes(1);
  fireEvent.click(document.querySelector('dialog'));
  expect(onClose).toHaveBeenCalledTimes(2);
  fireEvent.click(screen.getByText('Ruleta de 37 casillas.'));
  expect(onClose).toHaveBeenCalledTimes(2);
});
