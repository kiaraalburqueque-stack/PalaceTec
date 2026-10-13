
import { describe, it, expect, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';

import CasinoRoomCatalog from './CasinoRoomCatalog';

afterEach(() => {
  cleanup();
});

describe('HU09 - Catálogo de salas de casino', () => {
  it('muestra el título del catálogo', () => {
    render(<CasinoRoomCatalog />);

    expect(
      screen.getByRole('heading', { name: 'Salas de Casino' })
    ).toBeInTheDocument();
  });

  it('muestra las tres salas disponibles en el catálogo', () => {
    render(<CasinoRoomCatalog />);

    expect(screen.getByText('Ruleta Clásica')).toBeInTheDocument();
    expect(screen.getByText('Ruleta VIP')).toBeInTheDocument();
    expect(screen.getByText('Sala Premium')).toBeInTheDocument();
  });

  it('permite seleccionar una sala disponible', async () => {
    const user = userEvent.setup();

    render(<CasinoRoomCatalog />);

    const buttons = screen.getAllByRole('button', {
      name: 'Entrar a la sala',
    });

    await user.click(buttons[0]);

    expect(screen.getByRole('status')).toHaveTextContent(
      'Sala seleccionada: Ruleta Clásica'
    );
  });

  it('permite cambiar de sala seleccionada', async () => {
    const user = userEvent.setup();

    render(<CasinoRoomCatalog />);

    const buttons = screen.getAllByRole('button', {
      name: 'Entrar a la sala',
    });

    await user.click(buttons[0]);
    await user.click(buttons[1]);

    expect(screen.getByRole('status')).toHaveTextContent(
      'Sala seleccionada: Ruleta VIP'
    );
  });

  it('deshabilita el acceso a una sala no disponible', () => {
    render(<CasinoRoomCatalog />);

    expect(
      screen.getByRole('button', { name: 'No disponible' })
    ).toBeDisabled();
  });

  it('no muestra una sala seleccionada inicialmente', () => {
    render(<CasinoRoomCatalog />);

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});
