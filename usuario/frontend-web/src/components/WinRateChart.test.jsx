import { it, expect, afterEach } from 'vitest';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import WinRateChart, { winRateSlices } from './WinRateChart';
afterEach(cleanup);
it('HU51 calcula porcentajes exactos: ganadas entre totales por 100', () => {
  const slices = winRateSlices({ won: 1, lost: 2, push: 0, total: 3 });
  expect(slices.map((slice) => [slice.key, slice.value, slice.percent])).toEqual([['won', 1, 33.33], ['lost', 2, 66.67]]);
});
it('HU51 muestra totales y el detalle al pasar el cursor', () => {
  render(<WinRateChart data={{ won: 3, lost: 1, push: 0, total: 4, win_rate: 75 }} />);
  expect(screen.getByRole('img')).toHaveAccessibleName(/Win rate 75%/);
  expect(screen.getByText('Total liquidadas')).toBeInTheDocument();
  fireEvent.mouseEnter(screen.getByLabelText('Perdidas: 1 (25%)'));
  expect(screen.getByText('Perdidas: 1')).toBeInTheDocument();
});
it('HU51 avisa cuando no hay apuestas liquidadas', () => {
  render(<WinRateChart data={{ won: 0, lost: 0, push: 0, total: 0, win_rate: 0 }} />);
  expect(screen.getByText(/Aún no tienes apuestas liquidadas/)).toBeInTheDocument();
});
