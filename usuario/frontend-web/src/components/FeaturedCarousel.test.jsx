import { it, expect, vi, afterEach } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import '@testing-library/jest-dom/vitest';
import FeaturedCarousel from './FeaturedCarousel';
import { getFeaturedGames } from '../services/roomService';
vi.mock('../services/roomService', () => ({ getFeaturedGames: vi.fn() }));
afterEach(cleanup);
const renderIt = () => render(<MemoryRouter><FeaturedCarousel /></MemoryRouter>);

it('HU64 muestra los destacados y permite avanzar entre ellos', async () => {
  const user = userEvent.setup();
  getFeaturedGames.mockResolvedValue([
    { id: 1, title: 'Ruleta Imperial', category: 'RULETA', min_bet: '10.00', max_bet: '500.00', play_path: null, play_room_id: 4 },
    { id: 2, title: 'Blackjack 21', category: 'CARTAS', min_bet: '10.00', max_bet: '500.00', play_path: '/blackjack' }]);
  renderIt();
  expect(await screen.findByRole('heading', { name: 'Ruleta Imperial' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Jugar Ruleta Imperial' })).toHaveAttribute('href', '/ruleta/4');
  await user.click(screen.getByRole('button', { name: 'Destacado siguiente' }));
  expect(screen.getByRole('heading', { name: 'Blackjack 21' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Jugar Blackjack 21' })).toHaveAttribute('href', '/blackjack');
  await user.click(screen.getByRole('button', { name: 'Ver Ruleta Imperial' }));
  expect(screen.getByRole('heading', { name: 'Ruleta Imperial' })).toBeInTheDocument();
});

it('HU64 sin destacados no muestra el carrusel', async () => {
  getFeaturedGames.mockResolvedValue([]);
  const { container } = renderIt();
  await Promise.resolve();
  expect(container).toBeEmptyDOMElement();
});
