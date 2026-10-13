import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import RouletteWheel from './RouletteWheel';
import { StrictMode } from 'react';
import { WHEEL_NUMBERS, winningRotation } from '../utils/rouletteWheel';
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
describe('HU18 rueda sincronizada', () => {
  it('sitúa los 37 resultados bajo la flecha sin elegir ganador', () => {
    expect(new Set(WHEEL_NUMBERS).size).toBe(37);
    WHEEL_NUMBERS.forEach((number, index) => {
      const angle = winningRotation(number, 170);
      expect(angle).toBeGreaterThan(170 + 360 * 3);
      expect((angle + index * 360 / 37) % 360).toBeCloseTo(0, 7);
    });
  });
  it('muestra exactamente el cero enviado por el servidor con animación reducida', async () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true }));
    render(<StrictMode><RouletteWheel spinning={false} outcome={{ roundId: 12, number: 0 }} /></StrictMode>);
    await vi.waitFor(() => expect(screen.getByRole('img')).toHaveAttribute('data-winning-number', '0'));
  });
  it('indica el giro cuando se cierran apuestas sin inventar resultado', () => {
    render(<RouletteWheel spinning outcome={null} />);
    expect(screen.getByText(/La rueda está girando/)).toBeInTheDocument();
    expect(screen.getByRole('img')).toHaveAttribute('data-winning-number', '');
  });
});
