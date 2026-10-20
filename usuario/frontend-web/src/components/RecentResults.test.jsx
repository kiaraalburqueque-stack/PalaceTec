import { it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import RecentResults from './RecentResults';
it('HU33 limita a diez rondas únicas y conserva el cero', () => {
  const results = Array.from({ length: 11 }, (_, i) => ({ round_id: 12-i, number: i, color: i ? 'rojo' : 'verde' }));
  render(<RecentResults results={[results[0], ...results]} />);
  expect(screen.getAllByRole('listitem')).toHaveLength(10);
  expect(screen.getAllByRole('listitem')[0]).toHaveTextContent('Ronda 12: 0 · verde');
});
