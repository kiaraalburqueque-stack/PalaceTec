import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import RiskAlerts from './RiskAlerts';
import { getRiskAlerts, takePause } from '../services/responsibleService';
vi.mock('../services/responsibleService', () => ({ getRiskAlerts: vi.fn(), takePause: vi.fn() }));
afterEach(() => { cleanup(); sessionStorage.clear(); });
it('HU29 recupera incidentes y confirma una pausa en el servidor', async () => {
  getRiskAlerts.mockResolvedValue({ results: [{ incident_id: 7 }], pause_until: null });
  takePause.mockResolvedValue({ pause_until: new Date(Date.now() + 600000).toISOString() });
  const listener = vi.fn(); window.addEventListener('palacetec:pause', listener);
  const user = userEvent.setup(); render(<RiskAlerts userId={1} />);
  await user.click(await screen.findByRole('button', { name: 'Tomar pausa de 10 minutos' }));
  expect(takePause).toHaveBeenCalledWith(7);
  expect(await screen.findByRole('status')).toHaveTextContent('Pausa activa');
  expect(listener).toHaveBeenCalledOnce(); window.removeEventListener('palacetec:pause', listener);
});
