
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import LoginPage from './LoginPage';
import { UserContext } from '../context/UserContext';
import { login, getUserProfile } from '../services/authService';

vi.mock('../services/authService', () => ({
  login: vi.fn(),
  getUserProfile: vi.fn(),
}));

const navigateMock = vi.fn();

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal();

  return {
    ...actual,
    useNavigate: () => navigateMock,
  };
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('HU04 - Integración del login con el perfil real', () => {
  it('obtiene el perfil de Django y actualiza el usuario global', async () => {
    login.mockResolvedValue({});
    getUserProfile.mockResolvedValue({
      id: 1,
      email: 'jugador.demo@example.com',
      nickname: 'JugadorDemo',
      avatar_url: null,
    });

    const setUser = vi.fn();
    const user = userEvent.setup();

    render(
      <MemoryRouter>
        <UserContext.Provider value={{ user: null, setUser }}>
          <LoginPage />
        </UserContext.Provider>
      </MemoryRouter>
    );

    await user.type(
      screen.getByLabelText('Correo electrónico'),
      'jugador.demo@example.com'
    );

    await user.type(
      screen.getByLabelText('Contraseña'),
      'PalaceDemo2026!'
    );

    await user.click(
      screen.getByRole('button', { name: 'Iniciar sesión' })
    );

    expect(login).toHaveBeenCalledWith(
      'jugador.demo@example.com',
      'PalaceDemo2026!'
    );

    expect(getUserProfile).toHaveBeenCalled();

    expect(setUser).toHaveBeenCalledWith({
      id: 1,
      email: 'jugador.demo@example.com',
      name: 'JugadorDemo',
      nickname: 'JugadorDemo',
      avatar_url: null,
    });

    expect(navigateMock).toHaveBeenCalledWith('/');
  });

  it('muestra un error cuando falla el inicio de sesión', async () => {
    login.mockRejectedValue(new Error('Credenciales inválidas'));

    const user = userEvent.setup();

    render(
      <MemoryRouter>
        <UserContext.Provider value={{ user: null, setUser: vi.fn() }}>
          <LoginPage />
        </UserContext.Provider>
      </MemoryRouter>
    );

    await user.type(
      screen.getByLabelText('Correo electrónico'),
      'incorrecto@example.com'
    );

    await user.type(
      screen.getByLabelText('Contraseña'),
      'Incorrecta123!'
    );

    await user.click(
      screen.getByRole('button', { name: 'Iniciar sesión' })
    );

    expect(
        (await screen.findByRole('alert')).textContent
    ).toContain('Credenciales inválidas');
  });
});
