import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import GamesRanking from './GamesRanking';
import { getGamesRanking } from '../services/adminService';
vi.mock('../services/adminService', () => ({ getGamesRanking: vi.fn() }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });

const ranking = (days) => ({ days, from: '2026-11-16', to: '2026-11-22', timezone: 'America/Lima',
  totals: { rounds: 4, unique_players: 2, wagered: '480.00' },
  games: [{ game_id: 2, title: 'Blackjack 21', category: 'CARTAS', active: true, rounds: 2, unique_players: 1, wagered: '310.00', share_pct: 64.58 },
    { game_id: 1, title: 'Ruleta Europea', category: 'RULETA', active: true, rounds: 2, unique_players: 2, wagered: '170.00', share_pct: 35.42 }] });

it('HU71 muestra el ranking ordenado con barra proporcional y cambia a 30 días', async () => {
  getGamesRanking.mockImplementation((days) => Promise.resolve(ranking(days)));
  const user = userEvent.setup();
  const { container } = render(<GamesRanking />);
  const rows = await screen.findAllByRole('row');
  expect(within(rows[1]).getByText('Blackjack 21')).toBeInTheDocument();
  expect(rows[1]).toHaveTextContent('310.00 CRD');
  expect(rows[2]).toHaveTextContent('Ruleta Europea');
  const bars = container.querySelectorAll('.ranking-bar i');
  expect(bars[0]).toHaveStyle({ width: '100%' });
  expect(getGamesRanking).toHaveBeenCalledWith(7);
  await user.click(screen.getByRole('button', { name: 'Últimos 30 días' }));
  expect(getGamesRanking).toHaveBeenLastCalledWith(30);
  expect(screen.getByRole('button', { name: 'Últimos 30 días' })).toHaveAttribute('aria-pressed', 'true');
});

it('HU71 muestra el error de permisos', async () => {
  getGamesRanking.mockRejectedValue(new Error('No tiene permiso para realizar esta acción.'));
  render(<GamesRanking />);
  expect(await screen.findByRole('alert')).toHaveTextContent('permiso');
});
