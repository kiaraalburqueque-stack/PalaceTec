import { it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import GameCatalog from './GameCatalog';
import { getGames } from '../services/roomService';
vi.mock('../services/roomService', () => ({ getGames: vi.fn() }));
vi.mock('./CasinoRoomCatalog', () => ({ default: ({ gameId }) => <p>Salas del juego {gameId}</p> }));
it('HU34 usa juegos publicados y selecciona sus salas por ID', async () => {
  getGames.mockResolvedValue([{ id: 4, title: 'Ruleta Europea', min_bet: '10.00', max_bet: '500.00' }]);
  render(<GameCatalog />);
  expect(await screen.findByText('Salas del juego 4')).toBeInTheDocument();
  expect(screen.getByRole('button')).toHaveAttribute('aria-pressed', 'true');
});
