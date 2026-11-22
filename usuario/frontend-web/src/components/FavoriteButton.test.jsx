import { it, expect, vi, afterEach } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import FavoriteButton from './FavoriteButton';
import { toggleFavorite } from '../services/roomService';
vi.mock('../services/roomService', () => ({ toggleFavorite: vi.fn() }));
afterEach(cleanup);

const game = { id: 3, title: 'Ruleta Europea', is_favorite: false };

it('HU63 el corazón marca el juego como favorito y avisa al catálogo', async () => {
  const user = userEvent.setup();
  const onChange = vi.fn();
  toggleFavorite.mockResolvedValue({ game_id: 3, is_favorite: true });
  render(<FavoriteButton game={game} onChange={onChange} />);
  const heart = screen.getByRole('button', { name: 'Marcar Ruleta Europea como favorito' });
  expect(heart).toHaveAttribute('aria-pressed', 'false');
  await user.click(heart);
  expect(toggleFavorite).toHaveBeenCalledWith(3);
  expect(onChange).toHaveBeenCalledWith(3, true);
});

it('HU63 si el servidor falla no cambia el estado y muestra el error', async () => {
  const user = userEvent.setup();
  const onChange = vi.fn();
  toggleFavorite.mockRejectedValue(new Error('Sin conexión'));
  render(<FavoriteButton game={{ ...game, is_favorite: true }} onChange={onChange} />);
  await user.click(screen.getByRole('button', { name: 'Quitar Ruleta Europea de favoritos' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Sin conexión');
  expect(onChange).not.toHaveBeenCalled();
});
