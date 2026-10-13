
import { describe, it, expect, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';

import BetControls from './BetControls';

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
  });

  it('no muestra alerta si el saldo es suficiente', async () => {
    const user = userEvent.setup();

    render(<BetControls balance={1000} />);

    await user.click(screen.getByRole('button', { name: 'Apostar' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
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
});
