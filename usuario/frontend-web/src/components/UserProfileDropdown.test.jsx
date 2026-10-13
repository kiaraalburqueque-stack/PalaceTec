
import { describe, it, expect, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';

import UserProfileDropdown from './UserProfileDropdown';

afterEach(() => {
  cleanup();
});

const demoUser = {
  id: 1,
  name: 'Jugador de Prueba',
  nickname: 'JugadorDemo',
  email: 'jugador@palacetec.test',
};

describe('HU04 - Menú de perfil del usuario', () => {
  it('muestra el nickname del usuario', () => {
    render(<UserProfileDropdown user={demoUser} />);

    expect(screen.getByText('JugadorDemo')).toBeInTheDocument();
  });

  it('mantiene el menú cerrado inicialmente', () => {
    render(<UserProfileDropdown user={demoUser} />);

    expect(
      screen.queryByText('jugador@palacetec.test')
    ).not.toBeInTheDocument();
  });

  it('muestra los datos del perfil al abrir el menú', async () => {
    const user = userEvent.setup();

    render(<UserProfileDropdown user={demoUser} />);

    await user.click(
      screen.getByRole('button', { name: /JugadorDemo/i })
    );

    expect(
      screen.getByText(/Nombre: Jugador de Prueba/)
    ).toBeInTheDocument();

    expect(
      screen.getByText(/Correo: jugador@palacetec.test/)
    ).toBeInTheDocument();
  });

  it('cierra el menú al volver a hacer clic', async () => {
    const user = userEvent.setup();

    render(<UserProfileDropdown user={demoUser} />);

    const button = screen.getByRole('button', {
      name: /JugadorDemo/i,
    });

    await user.click(button);
    await user.click(button);

    expect(
      screen.queryByText('jugador@palacetec.test')
    ).not.toBeInTheDocument();
  });
});
