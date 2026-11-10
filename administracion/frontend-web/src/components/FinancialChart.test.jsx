import { it, expect, afterEach } from 'vitest';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import FinancialChart, { chartScale } from './FinancialChart';
afterEach(cleanup);
const days = [{ date: '2026-11-09', wagered: '100.00', paid: '40.00', net_margin: '60.00' }, { date: '2026-11-10', wagered: '50.00', paid: '80.00', net_margin: '-30.00' }];
const data = { days, totals: { wagered: '150.00', paid: '120.00', net_margin: '30.00' } };
it('HU48 alinea apostado y pagado del mismo día en una escala común', () => {
  const { top, y } = chartScale(days);
  expect(top).toBeGreaterThanOrEqual(100);
  expect(y(100)).toBeLessThan(y(40));
});
it('HU48 muestra el margen neto al pasar el cursor y los totales', () => {
  const { container } = render(<FinancialChart data={data} />);
  fireEvent.mouseEnter(container.querySelectorAll('svg > g')[5 + 1]);
  expect(screen.getByRole('status')).toHaveTextContent('Margen neto: -30.00 CRD');
  expect(screen.getByText('Total apostado').nextSibling).toHaveTextContent('150.00 CRD');
});
it('HU48 avisa cuando no hay datos', () => {
  render(<FinancialChart data={{ days: [{ date: '2026-11-10', wagered: '0.00', paid: '0.00', net_margin: '0.00' }], totals: { wagered: '0.00', paid: '0.00', net_margin: '0.00' } }} />);
  expect(screen.getByText(/Todavía no hay apuestas/)).toBeInTheDocument();
});
