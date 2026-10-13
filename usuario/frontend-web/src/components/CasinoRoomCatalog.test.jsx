
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';

import CasinoRoomCatalog from './CasinoRoomCatalog';
import { getCasinoRooms } from '../services/roomService';

vi.mock('../services/roomService', () => ({
  getCasinoRooms: vi.fn(),
}));

const mockRooms = [
  {
    id: 1,
    name: 'Ruleta baja',
    min_bet: '10.00',
    max_bet: '500.00',
    players: 0,
  },
  {
    id: 2,
    name: 'Ruleta media',
    min_bet: '50.00',
    max_bet: '2000.00',
    players: 0,
  },
  {
    id: 3,
    name: 'Ruleta alta',
    min_bet: '100.00',
    max_bet: '5000.00',
    players: 0,
  },
];

describe('HU09 - Catálogo de salas conectado con Django', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getCasinoRooms.mockResolvedValue(mockRooms);
  });

  afterEach(() => {
    cleanup();
  });

  it('muestra el título del catálogo', () => {
    render(<CasinoRoomCatalog />);

    expect(screen.getByText('Salas de Casino')).toBeInTheDocument();
  });

  it('consulta el servicio de salas', async () => {
    render(<CasinoRoomCatalog />);

    await screen.findByText('Ruleta baja');

    expect(getCasinoRooms).toHaveBeenCalledTimes(1);
  });

  it('muestra las tres salas recibidas del backend', async () => {
    render(<CasinoRoomCatalog />);

    expect(await screen.findByText('Ruleta baja')).toBeInTheDocument();
    expect(screen.getByText('Ruleta media')).toBeInTheDocument();
    expect(screen.getByText('Ruleta alta')).toBeInTheDocument();
  });

  it('muestra los límites de apuesta de las salas', async () => {
    render(<CasinoRoomCatalog />);

    expect(
      await screen.findByText(/Apuesta mínima: 10.00 CRD/)
    ).toBeInTheDocument();

    expect(
      screen.getByText(/Apuesta mínima: 50.00 CRD/)
    ).toBeInTheDocument();
  });

  it('permite seleccionar una sala disponible', async () => {
    const user = userEvent.setup();

    render(<CasinoRoomCatalog />);

    const buttons = await screen.findAllByRole('button', {
      name: 'Entrar a la sala',
    });

    await user.click(buttons[0]);

    expect(
      screen.getByText('Sala seleccionada: Ruleta baja')
    ).toBeInTheDocument();
  });

  it('permite cambiar la sala seleccionada', async () => {
    const user = userEvent.setup();

    render(<CasinoRoomCatalog />);

    const buttons = await screen.findAllByRole('button', {
      name: 'Entrar a la sala',
    });

    await user.click(buttons[0]);

    expect(
      screen.getByText('Sala seleccionada: Ruleta baja')
    ).toBeInTheDocument();

    await user.click(buttons[1]);

    expect(
      screen.getByText('Sala seleccionada: Ruleta media')
    ).toBeInTheDocument();
  });

  it('no muestra una sala seleccionada inicialmente', async () => {
    render(<CasinoRoomCatalog />);

    await screen.findByText('Ruleta baja');

    expect(
      screen.queryByText(/Sala seleccionada:/)
    ).not.toBeInTheDocument();
  });

  it('muestra un mensaje cuando el servidor falla', async () => {
    getCasinoRooms.mockRejectedValue(
      new Error('No se pudieron cargar las salas del casino.')
    );

    render(<CasinoRoomCatalog />);

    expect(
      await screen.findByRole('alert')
    ).toHaveTextContent('No se pudieron cargar las salas');
  });

  it('muestra un mensaje cuando no existen salas', async () => {
    getCasinoRooms.mockResolvedValue([]);

    render(<CasinoRoomCatalog />);

    expect(
      await screen.findByText('No hay salas disponibles actualmente.')
    ).toBeInTheDocument();
  });
});
