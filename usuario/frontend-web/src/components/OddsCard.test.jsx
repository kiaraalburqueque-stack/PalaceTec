import { it, expect, afterEach } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import OddsCard from './OddsCard';
afterEach(cleanup);

it('HU69 muestra probabilidad, pago y ventaja de la casa', () => {
  render(<OddsCard odds={{ label: 'Pleno al 17', probability: '1/37', probability_pct: '2.70', payout: '35:1', house_edge_pct: '2.70' }} />);
  const card = screen.getByLabelText('Probabilidades de Pleno al 17');
  expect(card).toHaveTextContent('Probabilidad1/37 (2.70 %)');
  expect(card).toHaveTextContent('Pago35 a 1');
  expect(card).toHaveTextContent('Ventaja de la casa2.70 %');
});
