import { it, expect, vi, beforeEach, afterEach } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import '@testing-library/jest-dom/vitest';
import GameCatalog from './GameCatalog';
import { getFavoriteGames, getGameCategories, getGames, toggleFavorite } from '../services/roomService';
vi.mock('../services/roomService', () => ({ getGames: vi.fn(), getGameCategories: vi.fn(), getFavoriteGames: vi.fn(), toggleFavorite: vi.fn() }));
vi.mock('./CasinoRoomCatalog', () => ({ default: ({ gameId }) => <p>Salas del juego {gameId}</p> }));

const roulette = { id: 4, title: 'Ruleta Europea', category: 'RULETA', min_bet: '10.00', max_bet: '500.00', rooms_url: '/api/v1/rooms?game=4', play_path: null };
const blackjack = { id: 7, title: 'Blackjack 21', category: 'CARTAS', min_bet: '10.00', max_bet: '500.00', rooms_url: null, play_path: '/blackjack' };
const counts = { total: 2, categories: [{ slug: 'ruleta', label: 'Ruleta', count: 1 }, { slug: 'cartas', label: 'Cartas', count: 1 },
  { slug: 'deportes', label: 'Deportes virtuales', count: 0 }, { slug: 'otros', label: 'Otros', count: 0 }] };

function Location() { return <output data-testid="location">{useLocation().search}</output>; }
function renderCatalog(url = '/') {
  return render(<MemoryRouter initialEntries={[url]}><GameCatalog /><Location /></MemoryRouter>);
}

afterEach(cleanup);
beforeEach(() => {
  getGameCategories.mockResolvedValue(counts);
  getGames.mockImplementation(({ category } = {}) => Promise.resolve(
    category === 'cartas' ? [blackjack] : category === 'ruleta' ? [roulette] : [roulette, blackjack]));
});

it('HU34 usa juegos publicados y selecciona sus salas por ID', async () => {
  renderCatalog();
  expect(await screen.findByText('Salas del juego 4')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Ver salas de Ruleta Europea' })).toHaveAttribute('aria-pressed', 'true');
});

it('HU61 muestra pestañas con contador y filtra sin recargar, guardando la categoría en la URL', async () => {
  const user = userEvent.setup();
  renderCatalog();
  const cards = await screen.findByRole('tab', { name: /Cartas/ });
  expect(cards).toHaveTextContent('1');
  expect(screen.getByRole('tab', { name: /Todos/ })).toHaveAttribute('aria-selected', 'true');
  expect(screen.getByRole('tab', { name: /Todos/ })).toHaveTextContent('2');
  await user.click(cards);
  expect(await screen.findByRole('link', { name: 'Jugar Blackjack 21' })).toHaveAttribute('href', '/blackjack');
  expect(screen.queryByText('Ruleta Europea')).not.toBeInTheDocument();
  expect(getGames).toHaveBeenLastCalledWith({ category: 'cartas', search: '' });
  expect(screen.getByTestId('location')).toHaveTextContent('?categoria=cartas');
  expect(cards).toHaveAttribute('aria-selected', 'true');
});

it('HU61 abre el catálogo en la categoría indicada por la URL', async () => {
  renderCatalog('/?categoria=ruleta');
  expect(await screen.findByText('Salas del juego 4')).toBeInTheDocument();
  expect(getGames).toHaveBeenCalledWith({ category: 'ruleta', search: '' });
  expect(await screen.findByRole('tab', { name: /Ruleta/ })).toHaveAttribute('aria-selected', 'true');
});

it('HU62 busca tras dejar de escribir y muestra el estado vacío con sugerencias de categorías', async () => {
  const user = userEvent.setup();
  getGames.mockImplementation(({ search } = {}) => Promise.resolve(search ? [] : [roulette, blackjack]));
  renderCatalog();
  await screen.findByText('Salas del juego 4');
  getGames.mockClear();
  await user.type(screen.getByRole('searchbox', { name: 'Buscar juegos' }), 'tragamonedas');
  expect(await screen.findByText('No se encontraron juegos.')).toBeInTheDocument();
  expect(getGames).toHaveBeenCalledTimes(1);
  expect(getGames).toHaveBeenCalledWith({ category: '', search: 'tragamonedas' });
  expect(getGameCategories).toHaveBeenLastCalledWith({ search: 'tragamonedas' });
  expect(screen.getByTestId('location')).toHaveTextContent('buscar=tragamonedas');
  await user.click(screen.getByRole('button', { name: 'Cartas' }));
  expect(await screen.findByRole('link', { name: 'Jugar Blackjack 21' })).toBeInTheDocument();
  expect(screen.getByRole('searchbox', { name: 'Buscar juegos' })).toHaveValue('');
});

it('HU62 el botón limpiar borra la búsqueda', async () => {
  const user = userEvent.setup();
  renderCatalog('/?buscar=ruleta');
  const input = screen.getByRole('searchbox', { name: 'Buscar juegos' });
  expect(input).toHaveValue('ruleta');
  await user.click(screen.getByRole('button', { name: 'Limpiar búsqueda' }));
  expect(input).toHaveValue('');
  expect(screen.queryByRole('button', { name: 'Limpiar búsqueda' })).not.toBeInTheDocument();
});

it('HU63 la pestaña Favoritos muestra la lista guardada y quita el juego al desmarcarlo', async () => {
  const user = userEvent.setup();
  getGameCategories.mockResolvedValue({ ...counts, favorites: 1 });
  getFavoriteGames.mockResolvedValue([{ ...blackjack, is_favorite: true }]);
  toggleFavorite.mockResolvedValue({ game_id: 7, is_favorite: false });
  renderCatalog();
  const tab = await screen.findByRole('tab', { name: /Favoritos/ });
  expect(tab).toHaveTextContent('1');
  await user.click(tab);
  const heart = await screen.findByRole('button', { name: 'Quitar Blackjack 21 de favoritos' });
  expect(heart).toHaveAttribute('aria-pressed', 'true');
  expect(getFavoriteGames).toHaveBeenCalledWith({ search: '' });
  expect(screen.getByTestId('location')).toHaveTextContent('?categoria=favoritos');
  await user.click(heart);
  expect(toggleFavorite).toHaveBeenCalledWith(7);
  expect(await screen.findByText(/Aún no tienes favoritos/)).toBeInTheDocument();
  expect(tab).toHaveTextContent('0');
});
