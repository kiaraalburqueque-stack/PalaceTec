import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import ResponsibleMonitor from './ResponsibleMonitor';
import { getResponsibleUsers } from '../services/adminService';
vi.mock('../services/adminService', () => ({ getResponsibleUsers: vi.fn() }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });
it('HU46 muestra insignias de estado y filtra por autoexcluidos', async () => {
  getResponsibleUsers.mockResolvedValue({ count: 1, summary: { limit_active: 1, self_excluded: 1, risk_alert: 0 }, results: [{
    id: 1, email: 'riesgo@palacetec.pe', dni: '12345678', is_active: true, daily_limit: '200.00', spent_today: '150.00', remaining_today: '50.00',
    self_excluded_until: '2026-11-17T00:00:00Z', pause_until: null, risk_alerts: 0, last_alert_at: null, statuses: ['LIMIT_ACTIVE', 'SELF_EXCLUDED'] }] });
  const user = userEvent.setup();
  render(<ResponsibleMonitor />);
  expect(await screen.findByText('Límite activo', { selector: '.badge' })).toBeInTheDocument();
  expect(screen.getByText('Autoexcluido', { selector: '.badge' })).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: /Autoexcluidos/ }));
  expect(getResponsibleUsers).toHaveBeenLastCalledWith({ status: 'SELF_EXCLUDED', page: 1 });
});
