
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import {
  MemoryRouter,
  Routes,
  Route,
  useParams,
} from 'react-router-dom';

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

function TestRoomPage() {
  const { roomId } = useParams();

  return <p>Sala abierta: {roomId}</p>;
}

function renderCatalog() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<CasinoRoomCatalog />} />
        <Route
          path="/ruleta/:roomId"
          element={<TestRoomPage />}
        />
      </Routes>
    </MemoryRouter>
  );
}

describe('HU09 - Catálogo de salas conectado con Django', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    getCasinoRooms.mockResolvedValue(mockRooms);
  });

  afterEach(() => {
    cleanup();
  });

  it('muestra el título del catálogo', () => {
    renderCatalog();

    expect(screen.getByText('Salas de Casino')).toBeInTheDocument();
  });

  it('consulta el servicio de salas', async () => {
    renderCatalog();

    await screen.findByText('Ruleta baja');

    expect(getCasinoRooms).toHaveBeenCalledTimes(1);
  });

  it('muestra las tres salas recibidas del backend', async () => {
    renderCatalog();

    expect(await screen.findByText('Ruleta baja')).toBeInTheDocument();
    expect(screen.getByText('Ruleta media')).toBeInTheDocument();
    expect(screen.getByText('Ruleta alta')).toBeInTheDocument();
  });

  it('muestra los límites de apuesta de las salas', async () => {
    renderCatalog();

    expect(
      await screen.findByText(/Apuesta mínima: 10.00 CRD/)
    ).toBeInTheDocument();

    expect(
      screen.getByText(/Apuesta mínima: 50.00 CRD/)
    ).toBeInTheDocument();
  });

  it('permite entrar a la sala seleccionada', async () => {
    const user = userEvent.setup();

    renderCatalog();

    const buttons = await screen.findAllByRole('button', {
      name: 'Entrar a la sala',
    });

    await user.click(buttons[0]);

    expect(
      screen.getByText('Sala abierta: 1')
    ).toBeInTheDocument();
  });

  it('navega a la sala correcta según su identificador', async () => {
    const user = userEvent.setup();

    renderCatalog();

    const buttons = await screen.findAllByRole('button', {
      name: 'Entrar a la sala',
    });

    await user.click(buttons[1]);

    expect(
      screen.getByText('Sala abierta: 2')
    ).toBeInTheDocument();
  });

  it('no muestra una sala seleccionada inicialmente', async () => {
    renderCatalog();

    await screen.findByText('Ruleta baja');

    expect(
      screen.queryByText(/Sala seleccionada:/)
    ).not.toBeInTheDocument();
  });

  it('muestra un mensaje cuando el servidor falla', async () => {
    getCasinoRooms.mockRejectedValue(
      new Error('No se pudieron cargar las salas del casino.')
    );

    renderCatalog();

    expect(
      await screen.findByRole('alert')
    ).toHaveTextContent('No se pudieron cargar las salas');
  });

  it('muestra un mensaje cuando no existen salas', async () => {
    getCasinoRooms.mockResolvedValue([]);

    renderCatalog();

    expect(
      await screen.findByText('No hay salas disponibles actualmente.')
    ).toBeInTheDocument();
  });
});
