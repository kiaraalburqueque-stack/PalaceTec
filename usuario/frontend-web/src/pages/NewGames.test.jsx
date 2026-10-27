import { beforeEach, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import '@testing-library/jest-dom/vitest';
import BlackjackPage from './BlackjackPage';
import SportsPage from './SportsPage';
import * as casino from '../services/casinoService';

const { testUser } = vi.hoisted(() => ({ testUser: { id: 1 } }));
vi.mock('../hooks/useUser', () => ({ default: () => ({ user: testUser, loading: false }) }));
vi.mock('../hooks/useGameWallet', () => ({ default: () => ({ wallet: { balance: '1000.00' }, refresh: vi.fn() }) }));
vi.mock('../services/casinoService', () => ({ getBlackjack: vi.fn(), startBlackjack: vi.fn(), actBlackjack: vi.fn(), getSportEvents: vi.fn(), getSportTickets: vi.fn(), placeSportTicket: vi.fn() }));
afterEach(cleanup);
beforeEach(() => vi.clearAllMocks());

it('reparte blackjack, conserva la carta oculta y permite plantarse', async () => {
  casino.getBlackjack.mockResolvedValue({ hand: null });
  const hand = { id: 1, amount: '10.00', player: ['10_S', '8_H'], dealer: ['9_D'], dealer_hidden: true, player_score: 18, dealer_score: 9, active: true, revision: 0 };
  casino.startBlackjack.mockResolvedValue({ hand });
  casino.actBlackjack.mockResolvedValue({ hand: { ...hand, dealer: ['9_D', '8_C'], dealer_hidden: false, active: false, revision: 1, outcome: 'WON', payout: '20.00' } });
  render(<MemoryRouter><BlackjackPage /></MemoryRouter>);
  await waitFor(() => expect(screen.getByRole('button', { name: 'Repartir cartas' })).toBeEnabled());
  fireEvent.click(screen.getByRole('button', { name: 'Repartir cartas' }));
  expect(await screen.findByLabelText('Carta oculta de la banca')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Plantarse' }));
  expect(await screen.findByText('¡Ganaste la mano!')).toBeInTheDocument();
  expect(casino.actBlackjack).toHaveBeenCalledWith(1, { action: 'stand', revision: 0 });
});

it('identifica deportes como simulación y envía cuota y referencia del boleto', async () => {
  casino.getSportEvents.mockResolvedValue({ simulation: true, server_time: new Date().toISOString(), events: [{ id: 4, home: 'Cóndores FC', away: 'Halcones FC', starts_at: new Date(Date.now()+600000).toISOString(), phase: 'OPEN', odds: { HOME: '1.90', DRAW: '3.20', AWAY: '2.40' }, score: null }] });
  casino.getSportTickets.mockResolvedValue([]);
  casino.placeSportTicket.mockResolvedValue({ id: 1 });
  render(<MemoryRouter><SportsPage /></MemoryRouter>);
  fireEvent.click(await screen.findByRole('button', { name: 'Cóndores FC cuota 1.90' }));
  expect(screen.getByText('19.00 CRD')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Confirmar apuesta' }));
  expect(await screen.findByText(/Boleto registrado/)).toBeInTheDocument();
  expect(casino.placeSportTicket).toHaveBeenCalledWith(expect.objectContaining({ event_id: 4, selection: 'HOME', quoted_odds: '1.90', amount: '10', reference: expect.any(String) }));
  expect(screen.getByText(/No son partidos reales/)).toBeInTheDocument();
});

it('un fallo de red reintenta la misma mano sin generar otra referencia', async () => {
  casino.getBlackjack.mockResolvedValue({ hand: null });
  casino.startBlackjack.mockRejectedValueOnce(new Error('Red no disponible')).mockResolvedValueOnce({ hand: { id: 1, amount: '10.00', player: ['A_S', 'K_H'], dealer: ['9_C', '8_D'], dealer_hidden: false, player_score: 21, dealer_score: 17, active: false, revision: 0, outcome: 'BLACKJACK', payout: '25.00' } });
  render(<MemoryRouter><BlackjackPage /></MemoryRouter>);
  await waitFor(() => expect(screen.getByRole('button', { name: 'Repartir cartas' })).toBeEnabled());
  fireEvent.click(screen.getByRole('button', { name: 'Repartir cartas' }));
  fireEvent.click(await screen.findByRole('button', { name: 'Reintentar misma mano' }));
  expect(await screen.findByText('¡Blackjack natural!')).toBeInTheDocument();
  expect(casino.startBlackjack.mock.calls[0][0]).toEqual(casino.startBlackjack.mock.calls[1][0]);
});
