import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import Header from './Header';
import UserProvider from '../context/UserProvider';
import { getUserProfile } from '../services/authService';
import { getWalletSummary } from '../services/walletService';
vi.mock('../services/authService', () => ({ getUserProfile: vi.fn() }));
vi.mock('../services/walletService', () => ({ getWalletSummary: vi.fn() }));
beforeEach(() => { getUserProfile.mockResolvedValue({ id: 1, email: 'real@example.com', nickname: 'Kiara' }); getWalletSummary.mockResolvedValue({ balance: '1000.00', bonus_balance: '1000.00', winnings_balance: '0.00' }); });
afterEach(() => { cleanup(); vi.clearAllMocks(); });
describe('HU04 - perfil real', () => {
  it('muestra saldo real y lo actualiza al recibir un premio', async () => {
    render(<UserProvider><Header /></UserProvider>);
    expect(await screen.findByText('1,000 CRD')).toBeInTheDocument();
    act(() => window.dispatchEvent(new CustomEvent('palacetec:wallet-update', { detail: { userId: 1, wallet: { balance: '1350.00', bonus_balance: '990.00', winnings_balance: '360.00' } } })));
    expect(screen.getByText('1,350 CRD')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Kiara/ }));
    expect(screen.getByText(/Saldo disponible:/)).toHaveTextContent('1,350 CRD');
    expect(screen.getByText(/Bono: 990.00/)).toBeInTheDocument();
  });
  it('muestra PalaceTec', async () => { render(<UserProvider><Header /></UserProvider>); expect(screen.getByText('PalaceTec')).toBeInTheDocument(); await screen.findByText('Kiara'); });
  it('restaura el perfil desde Django', async () => { render(<UserProvider><Header /></UserProvider>); expect(await screen.findByText('Kiara')).toBeInTheDocument(); });
  it('muestra datos reales al abrir el menú', async () => { render(<UserProvider><Header /></UserProvider>); await userEvent.click(await screen.findByRole('button', { name: /Kiara/ })); expect(screen.getByText(/real@example.com/)).toBeInTheDocument(); });
  it('no inventa un usuario si no hay sesión', async () => { getUserProfile.mockRejectedValue({ status: 401 }); render(<UserProvider><Header /></UserProvider>); expect(await screen.findByText('Sin sesión')).toBeInTheDocument(); expect(screen.queryByText('JugadorDemo')).not.toBeInTheDocument(); });
});
