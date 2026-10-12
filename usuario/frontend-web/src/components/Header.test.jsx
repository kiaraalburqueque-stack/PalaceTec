import { describe, it, expect, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';

import Header from './Header';
import UserProvider from '../context/UserProvider';

afterEach(() => {
  cleanup();
});

describe('HU04 - Header con React Context', () => {
  it('muestra el nombre de PalaceTec', () => {
    render(
      <UserProvider>
        <Header />
      </UserProvider>
    );

    expect(screen.getByText('PalaceTec')).toBeInTheDocument();
  });

  it('obtiene el nickname desde el contexto global', () => {
    render(
      <UserProvider>
        <Header />
      </UserProvider>
    );

    expect(screen.getByText('JugadorDemo')).toBeInTheDocument();
  });

  it('muestra los datos del perfil al abrir el menú', async () => {
    const user = userEvent.setup();

    render(
      <UserProvider>
        <Header />
      </UserProvider>
    );

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
});
