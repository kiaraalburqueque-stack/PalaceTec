import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import DeactivateGame from './DeactivateGame';
import { softDeleteGame } from '../services/adminService';
vi.mock('../services/adminService', () => ({ softDeleteGame: vi.fn(), restoreGame: vi.fn() }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });
it('HU45 pide confirmación antes de desactivar el juego', async () => {
  softDeleteGame.mockResolvedValue({});
  const onChanged = vi.fn(); const user = userEvent.setup();
  render(<DeactivateGame game={{ id: 4, title: 'Ruleta Imperial', deleted_at: null }} onChanged={onChanged} />);
  await user.click(screen.getByRole('button', { name: 'Desactivar juego' }));
  expect(screen.getByText(/se conservan/)).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: 'Sí, desactivar' }));
  expect(softDeleteGame).toHaveBeenCalledWith(4);
  expect(onChanged).toHaveBeenCalledWith(expect.stringMatching(/desactivado/));
});
it('HU45 un juego desactivado ofrece restaurarlo', () => {
  render(<DeactivateGame game={{ id: 4, title: 'X', deleted_at: '2026-11-10T00:00:00Z' }} onChanged={() => {}} />);
  expect(screen.getByRole('button', { name: 'Restaurar' })).toBeInTheDocument();
});
