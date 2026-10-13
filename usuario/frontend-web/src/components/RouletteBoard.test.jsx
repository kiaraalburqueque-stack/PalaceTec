
import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';

import RouletteBoard from './RouletteBoard';

afterEach(() => {
  cleanup();
});

describe('HU11 - Tapete numérico interactivo de ruleta', () => {
  it('muestra los 37 números del 0 al 36', () => {
    render(<RouletteBoard onSelectNumber={() => {}} />);

    for (let number = 0; number <= 36; number++) {
      expect(
        screen.getByRole('button', { name: `Número ${number}` })
      ).toBeInTheDocument();
    }
  });

  it('permite seleccionar un número haciendo clic', async () => {
    const user = userEvent.setup();
    const onSelectNumber = vi.fn();

    render(<RouletteBoard onSelectNumber={onSelectNumber} />);

    await user.click(screen.getByRole('button', { name: 'Número 17' }));

    expect(onSelectNumber).toHaveBeenCalledWith(17);
  });

  it('marca visualmente el número seleccionado', () => {
    render(
      <RouletteBoard
        selectedNumber={17}
        onSelectNumber={() => {}}
      />
    );

    expect(
      screen.getByRole('button', { name: 'Número 17' })
    ).toHaveAttribute('aria-pressed', 'true');

    expect(
      screen.getByText('Número seleccionado:')
    ).toBeInTheDocument();
  });

  it('permite seleccionar un número con el teclado', async () => {
    const user = userEvent.setup();
    const onSelectNumber = vi.fn();

    render(<RouletteBoard onSelectNumber={onSelectNumber} />);

    const numberButton = screen.getByRole('button', {
      name: 'Número 20',
    });

    numberButton.focus();
    await user.keyboard('{Enter}');

    expect(onSelectNumber).toHaveBeenCalledWith(20);
  });

  it('no permite seleccionar números cuando está deshabilitado', async () => {
    const user = userEvent.setup();
    const onSelectNumber = vi.fn();

    render(
      <RouletteBoard
        disabled
        onSelectNumber={onSelectNumber}
      />
    );

    await user.click(screen.getByRole('button', { name: 'Número 10' }));

    expect(onSelectNumber).not.toHaveBeenCalled();
  });
});
