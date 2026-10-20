import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import RealityCheck from './RealityCheck';
import { getSessionSummary } from '../services/responsibleService';
vi.mock('../services/responsibleService', () => ({ getSessionSummary: vi.fn() }));
vi.mock('../services/authService', () => ({ logout: vi.fn() }));
afterEach(() => { cleanup(); sessionStorage.clear(); });
it('HU28 restaura la hora del servidor y conserva la decisión al recargar', async () => {
  getSessionSummary.mockResolvedValue({ session_started_at: 'session-1', duration_seconds: 3601, reality_check_interval_seconds: 3600, total_wagered: '10.00', total_paid: '0.00', net: '-10.00', pending_wagered: '0.00' });
  const user = userEvent.setup(); const view = render(<RealityCheck />);
  expect(await screen.findByRole('dialog')).toHaveTextContent('60 minutos');
  await user.click(screen.getByRole('button', { name: 'Continuar jugando' }));
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  view.unmount(); render(<RealityCheck />);
  await vi.waitFor(() => expect(getSessionSummary).toHaveBeenCalledTimes(2));
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
});
