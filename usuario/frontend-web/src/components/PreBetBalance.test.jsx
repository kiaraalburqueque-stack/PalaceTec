
import { describe, it, expect, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';

import PreBetBalance from './PreBetBalance';

afterEach(() => {
  cleanup();
});

describe('HU07 - Resumen visual pre-apuesta', () => {
  it('muestra el saldo disponible formateado', () => {
    render(<PreBetBalance balance={1000} />);

    expect(screen.getByText('1,000 CRD')).toBeInTheDocument();
  });

  it('mantiene el tooltip cerrado inicialmente', () => {
    render(<PreBetBalance balance={1000} />);

    expect(
      screen.queryByRole('status')
    ).not.toBeInTheDocument();
  });

  it('abre el tooltip al pulsar el botón', async () => {
    const user = userEvent.setup();

    render(<PreBetBalance balance={1000} />);

    await user.click(
      screen.getByRole('button', {
        name: 'Información del saldo',
      })
    );

    expect(screen.getByRole('status')).toHaveTextContent(
      '1,000 CRD'
    );
  });

  it('cierra el tooltip al pulsar nuevamente', async () => {
    const user = userEvent.setup();

    render(<PreBetBalance balance={1000} />);

    const button = screen.getByRole('button', {
      name: 'Información del saldo',
    });

    await user.click(button);
    await user.click(button);

    expect(
      screen.queryByRole('status')
    ).not.toBeInTheDocument();
  });
});
