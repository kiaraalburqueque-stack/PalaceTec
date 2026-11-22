import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import FeaturedToggle from './FeaturedToggle';
import { setGameFeatured } from '../services/adminService';
vi.mock('../services/adminService', () => ({ setGameFeatured: vi.fn() }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });

it('HU64 el interruptor marca el juego como destacado', async () => {
  setGameFeatured.mockResolvedValue({ id: 4, is_featured: true });
  const onChanged = vi.fn(); const user = userEvent.setup();
  render(<FeaturedToggle game={{ id: 4, title: 'Ruleta Imperial', is_featured: false }} onChanged={onChanged} />);
  const toggle = screen.getByRole('switch', { name: 'Destacar Ruleta Imperial en el inicio' });
  expect(toggle).not.toBeChecked();
  await user.click(toggle);
  expect(setGameFeatured).toHaveBeenCalledWith(4, true);
  expect(onChanged).toHaveBeenCalledWith(expect.stringMatching(/carrusel/));
});

it('HU64 muestra el error al superar los 5 destacados', async () => {
  setGameFeatured.mockRejectedValue(new Error('Ya hay 5 juegos destacados. Quita uno antes de destacar otro.'));
  const onChanged = vi.fn(); const user = userEvent.setup();
  render(<FeaturedToggle game={{ id: 6, title: 'Sexto', is_featured: false }} onChanged={onChanged} />);
  await user.click(screen.getByRole('switch'));
  expect(await screen.findByRole('alert')).toHaveTextContent('Ya hay 5 juegos destacados');
  expect(onChanged).not.toHaveBeenCalled();
});

it('HU64 un juego destacado aparece activado y se puede quitar', async () => {
  setGameFeatured.mockResolvedValue({ id: 2, is_featured: false });
  const onChanged = vi.fn(); const user = userEvent.setup();
  render(<FeaturedToggle game={{ id: 2, title: 'Blackjack 21', is_featured: true }} onChanged={onChanged} />);
  expect(screen.getByRole('switch')).toBeChecked();
  await user.click(screen.getByRole('switch'));
  expect(setGameFeatured).toHaveBeenCalledWith(2, false);
  expect(onChanged).toHaveBeenCalledWith('Blackjack 21 ya no está destacado.');
});
