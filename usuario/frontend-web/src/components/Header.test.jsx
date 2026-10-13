import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import Header from './Header';
import UserProvider from '../context/UserProvider';
import { getUserProfile } from '../services/authService';
vi.mock('../services/authService', () => ({ getUserProfile: vi.fn() }));
beforeEach(() => { getUserProfile.mockResolvedValue({ id: 1, email: 'real@example.com', nickname: 'Kiara' }); });
afterEach(() => { cleanup(); vi.clearAllMocks(); });
describe('HU04 - perfil real', () => {
  it('muestra PalaceTec', async () => { render(<UserProvider><Header /></UserProvider>); expect(screen.getByText('PalaceTec')).toBeInTheDocument(); await screen.findByText('Kiara'); });
  it('restaura el perfil desde Django', async () => { render(<UserProvider><Header /></UserProvider>); expect(await screen.findByText('Kiara')).toBeInTheDocument(); });
  it('muestra datos reales al abrir el menú', async () => { render(<UserProvider><Header /></UserProvider>); await userEvent.click(await screen.findByRole('button', { name: /Kiara/ })); expect(screen.getByText(/real@example.com/)).toBeInTheDocument(); });
  it('no inventa un usuario si no hay sesión', async () => { getUserProfile.mockRejectedValue({ status: 401 }); render(<UserProvider><Header /></UserProvider>); expect(await screen.findByText('Sin sesión')).toBeInTheDocument(); expect(screen.queryByText('JugadorDemo')).not.toBeInTheDocument(); });
});
