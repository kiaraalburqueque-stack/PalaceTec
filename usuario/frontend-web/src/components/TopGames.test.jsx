import { it, expect, vi, afterEach } from 'vitest';
import { cleanup, render, screen, within } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import TopGames from './TopGames';
import { getTopGames } from '../services/statsService';
vi.mock('../services/statsService', () => ({ getTopGames: vi.fn() }));
afterEach(cleanup);

it('HU67 muestra el ranking en orden con barras proporcionales', async () => {
  getTopGames.mockResolvedValue({ total_rounds: 5, results: [
    { game_id: 2, title: 'Blackjack 21', rounds: 4, wagered: '40.00' },
    { game_id: 1, title: 'Ruleta Europea', rounds: 1, wagered: '110.00' }] });
  const { container } = render(<TopGames />);
  const items = await screen.findAllByRole('listitem');
  expect(within(items[0]).getByText('Blackjack 21')).toBeInTheDocument();
  expect(items[0]).toHaveTextContent('4 rondas · 40.00 CRD');
  expect(items[1]).toHaveTextContent('1 ronda · 110.00 CRD');
  const bars = container.querySelectorAll('.top-games-bar span');
  expect(bars[0]).toHaveStyle({ width: '100%' });
  expect(bars[1]).toHaveStyle({ width: '25%' });
});

it('HU67 muestra el estado vacío si aún no juega', async () => {
  getTopGames.mockResolvedValue({ total_rounds: 0, results: [] });
  render(<TopGames />);
  expect(await screen.findByText('Aún no tienes juegos jugados.')).toBeInTheDocument();
});
