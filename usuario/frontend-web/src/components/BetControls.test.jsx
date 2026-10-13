
import {
  describe,
  it,
  expect,
  beforeEach,
  afterEach,
  vi,
} from 'vitest';
import {
  render,
  screen,
  cleanup,
  waitFor,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';

import BetControls from './BetControls';
import { checkPreBet } from '../services/walletService';

vi.mock('../services/walletService', () => ({
  checkPreBet: vi.fn(),
}));

beforeEach(() => {
  vi.clearAllMocks();

  checkPreBet.mockResolvedValue({
    balance: '1000.00',
    can_bet: true,
  });
});

afterEach(() => {
  cleanup();
});

describe('HU08 - Control de saldo insuficiente', () => {
  it('muestra el saldo disponible', () => {
    render(<BetControls balance={1000} />);

    expect(screen.getByText(/1,000 CRD/)).toBeInTheDocument();
  });

  it('muestra las fichas disponibles', () => {
    render(<BetControls balance={1000} />);

    expect(screen.getByRole('button', { name: '10' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '50' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '100' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '500' })).toBeInTheDocument();
  });

  it('muestra la alerta cuando el saldo es cero', async () => {
    const user = userEvent.setup();

    render(<BetControls balance={0} />);

    await user.click(screen.getByRole('button', { name: 'Apostar' }));

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText('Saldo Insuficiente')).toBeInTheDocument();
    expect(checkPreBet).not.toHaveBeenCalled();
  });

  it('no muestra alerta si el saldo es suficiente', async () => {
    const user = userEvent.setup();

    render(<BetControls balance={1000} />);

    await user.click(screen.getByRole('button', { name: 'Apostar' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    expect(
      await screen.findByText(/Saldo validado correctamente/)
    ).toBeInTheDocument();
  });

  it('permite cerrar la alerta', async () => {
    const user = userEvent.setup();

    render(<BetControls balance={0} />);

    await user.click(screen.getByRole('button', { name: 'Apostar' }));
    await user.click(screen.getByRole('button', { name: 'Cerrar' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('activa la animación cuando el saldo es insuficiente', async () => {
    const user = userEvent.setup();
    const { container } = render(<BetControls balance={0} />);

    await user.click(screen.getByRole('button', { name: 'Apostar' }));

    expect(container.querySelector('.bet-chips')).toHaveClass('shake');
  });

  it('consulta a Django cuando el saldo local es suficiente', async () => {
    const user = userEvent.setup();

    render(<BetControls balance={1000} />);

    await user.click(screen.getByRole('button', { name: '50' }));
    await user.click(screen.getByRole('button', { name: 'Apostar' }));

    await waitFor(() => {
      expect(checkPreBet).toHaveBeenCalledWith(50);
    });

    expect(checkPreBet).toHaveBeenCalledTimes(1);
  });

  it('muestra saldo insuficiente si Django rechaza la apuesta', async () => {
    checkPreBet.mockResolvedValue({
      balance: '5.00',
      can_bet: false,
    });

    const user = userEvent.setup();

    render(<BetControls balance={1000} />);

    await user.click(screen.getByRole('button', { name: 'Apostar' }));

    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText('Saldo Insuficiente')).toBeInTheDocument();
  });

  it('muestra un mensaje cuando Django valida el saldo', async () => {
    const user = userEvent.setup();

    render(<BetControls balance={1000} />);

    await user.click(screen.getByRole('button', { name: 'Apostar' }));

    expect(
      await screen.findByRole('status')
    ).toHaveTextContent('Saldo validado correctamente');

    expect(checkPreBet).toHaveBeenCalledWith(10);
  });

  it('muestra un error si falla la conexión con Django', async () => {
    checkPreBet.mockRejectedValue(
      new Error('No se pudo validar la apuesta.')
    );

    const user = userEvent.setup();

    render(<BetControls balance={1000} />);

    await user.click(screen.getByRole('button', { name: 'Apostar' }));

    expect(
      await screen.findByRole('alert')
    ).toHaveTextContent('No se pudo validar la apuesta.');
  });

  it('evita validaciones duplicadas mientras espera al servidor', async () => {
    let resolveCheck;

    checkPreBet.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveCheck = resolve;
        })
    );

    const user = userEvent.setup();

    render(<BetControls balance={1000} />);

    await user.click(screen.getByRole('button', { name: 'Apostar' }));

    expect(
      screen.getByRole('button', { name: 'Procesando...' })
    ).toBeDisabled();

    expect(checkPreBet).toHaveBeenCalledTimes(1);

    resolveCheck({
      balance: '1000.00',
      can_bet: true,
    });

    expect(
      await screen.findByText(/Saldo validado correctamente/)
    ).toBeInTheDocument();

    expect(checkPreBet).toHaveBeenCalledTimes(1);
  });
});
