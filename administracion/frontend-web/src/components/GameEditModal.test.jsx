import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import GameEditModal from './GameEditModal';
import { updateGame } from '../services/adminService';
vi.mock('../services/adminService', () => ({ updateGame: vi.fn() }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });
const game = { id: 1, title: 'Ruleta Europea', min_bet: '10.00', max_bet: '500.00', rtp: '97.30' };
it('HU44 precarga los datos y rechaza min_bet mayor que max_bet', async () => {
  const user = userEvent.setup();
  render(<GameEditModal game={game} onClose={() => {}} onSaved={() => {}} />);
  expect(screen.getByLabelText('RTP (%)')).toHaveValue(97.3);
  const min = screen.getByLabelText('Apuesta mínima (CRD)');
  await user.clear(min); await user.type(min, '900');
  await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));
  expect(screen.getByText('La apuesta mínima no puede ser mayor que la máxima.')).toBeInTheDocument();
  expect(updateGame).not.toHaveBeenCalled();
});
it('HU44 guarda los nuevos parámetros', async () => {
  updateGame.mockResolvedValue({ ...game, rtp: '96.00' });
  const onSaved = vi.fn(); const user = userEvent.setup();
  render(<GameEditModal game={game} onClose={() => {}} onSaved={onSaved} />);
  const rtp = screen.getByLabelText('RTP (%)');
  await user.clear(rtp); await user.type(rtp, '96');
  await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));
  expect(updateGame).toHaveBeenCalledWith(1, expect.objectContaining({ rtp: '96' }));
  expect(onSaved).toHaveBeenCalled();
});
