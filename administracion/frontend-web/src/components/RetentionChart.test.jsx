import { it, expect, afterEach } from 'vitest';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import RetentionChart from './RetentionChart';
afterEach(cleanup);

const days = Array.from({ length: 30 }, (_, index) => ({ date: `2026-11-${String(index + 1).padStart(2, '0')}`, dau: index === 29 ? 2 : index === 28 ? 1 : 0 }));
const data = { from: '2026-11-01', to: '2026-11-30', days, mau: 3, average_dau: 0.1, today_dau: 2, dau_mau_ratio: 3.33 };

it('HU72 dibuja la línea de DAU y destaca el índice DAU/MAU', () => {
  const { container } = render(<RetentionChart data={data} />);
  expect(screen.getByLabelText('Índice DAU/MAU 3.33%')).toHaveTextContent('3.33%');
  expect(container.querySelector('.retention-line').getAttribute('d').split('L')).toHaveLength(30);
  expect(screen.getByText('Activos hoy (DAU)').nextSibling).toHaveTextContent('2');
  expect(screen.getByText('Activos en 30 días (MAU)').nextSibling).toHaveTextContent('3');
  fireEvent.mouseEnter(container.querySelectorAll('svg g')[container.querySelectorAll('svg g').length - 1]);
  expect(screen.getByRole('status')).toHaveTextContent('2 usuarios activos');
});

it('HU72 avisa cuando no hay jugadores activos', () => {
  render(<RetentionChart data={{ ...data, days: days.map((d) => ({ ...d, dau: 0 })), mau: 0, today_dau: 0, average_dau: 0, dau_mau_ratio: 0 }} />);
  expect(screen.getByText(/Todavía no hay jugadores activos/)).toBeInTheDocument();
});
