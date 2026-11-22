import { it, expect, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import KpiCard from './KpiCard';
afterEach(cleanup);
it('HU47 indica subida en verde y bajada en rojo frente a ayer', () => {
  const { container, rerender } = render(<KpiCard label="Créditos apostados hoy" value="150.00 CRD" change={50} previous="100.00 CRD" />);
  expect(screen.getByLabelText('Variación frente a ayer: +50%')).toHaveTextContent('▲');
  expect(container.querySelector('.kpi-change')).toHaveClass('good');
  rerender(<KpiCard label="Créditos pagados hoy" value="200.00 CRD" change={20} invert />);
  expect(container.querySelector('.kpi-change')).toHaveClass('bad');
  rerender(<KpiCard label="Ganancia" value="0.00 CRD" change={null} />);
  expect(screen.getByText(/Sin dato de ayer/)).toBeInTheDocument();
});
