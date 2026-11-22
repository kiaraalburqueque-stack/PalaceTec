import { it, expect, vi, afterEach } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import '@testing-library/jest-dom/vitest';
import ForYou from './ForYou';
import { getRecommendations } from '../services/roomService';
vi.mock('../services/roomService', () => ({ getRecommendations: vi.fn() }));
afterEach(cleanup);
const renderIt = () => render(<MemoryRouter><ForYou /></MemoryRouter>);

it('HU70 muestra hasta 4 juegos con el motivo y el enlace para jugar', async () => {
  getRecommendations.mockResolvedValue({ blocked: null, personalized: true, results: [
    { id: 7, title: 'Blackjack 21', category: 'CARTAS', reason: 'Porque jugaste Blackjack 21', play_path: '/blackjack' },
    { id: 1, title: 'Ruleta Europea', category: 'RULETA', reason: 'Está en tus favoritos', play_path: null, play_room_id: 3 }] });
  renderIt();
  expect(await screen.findByRole('heading', { name: 'Para ti' })).toBeInTheDocument();
  expect(screen.getByText('Porque jugaste Blackjack 21')).toBeInTheDocument();
  expect(screen.getByText('SEGÚN TU ACTIVIDAD')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Jugar Blackjack 21' })).toHaveAttribute('href', '/blackjack');
  expect(screen.getByRole('link', { name: 'Jugar Ruleta Europea' })).toHaveAttribute('href', '/ruleta/3');
});

it('HU70 con el límite diario alcanzado no recomienda juegos', async () => {
  getRecommendations.mockResolvedValue({ blocked: 'daily_limit_reached', personalized: false, results: [] });
  renderIt();
  expect(await screen.findByRole('status')).toHaveTextContent('Alcanzaste tu límite diario');
  expect(screen.queryByRole('link')).not.toBeInTheDocument();
});

it('HU70 un jugador nuevo ve lo más jugado', async () => {
  getRecommendations.mockResolvedValue({ blocked: null, personalized: false, results: [
    { id: 9, title: 'Deportes virtuales', category: 'DEPORTES', reason: 'Popular en PalaceTec', play_path: '/deportes' }] });
  renderIt();
  expect(await screen.findByText('LO MÁS JUGADO')).toBeInTheDocument();
  expect(screen.getByText('Popular en PalaceTec')).toBeInTheDocument();
});
