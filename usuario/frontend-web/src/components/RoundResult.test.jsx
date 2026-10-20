import { it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import RoundResult from './RoundResult';
it('HU23 presenta cero verde sin paridad usando el resultado confirmado', () => {
  render(<RoundResult result={{ round_id: 7, winning_number: 0, color: 'verde', parity: null }} />);
  expect(screen.getByText('0')).toBeInTheDocument();
  expect(screen.getByText(/verde.*Sin paridad/)).toBeInTheDocument();
});
