import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import AuditLogs from './AuditLogs';
import { getAuditLogs } from '../services/adminService';
vi.mock('../services/adminService', () => ({ getAuditLogs: vi.fn() }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });
it('HU50 lista administrador, acción, recurso, IP y fecha, con búsqueda', async () => {
  getAuditLogs.mockResolvedValue({ count: 1, results: [{ id: 1, created_at: '2026-11-10T12:00:00Z', admin: 'admin@palacetec.pe', action: 'PUT admin-game-detail',
    method: 'PUT', resource: '/api/v1/admin/games/1', status_code: 200, ip_address: '10.0.0.7', details: { rtp: '95.00' } }] });
  const user = userEvent.setup();
  render(<AuditLogs />);
  expect(await screen.findByText('/api/v1/admin/games/1')).toBeInTheDocument();
  expect(screen.getByText('10.0.0.7')).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: 'Ver' }));
  expect(screen.getByText(/"rtp": "95.00"/)).toBeInTheDocument();
  await user.type(screen.getByLabelText('Buscar en auditoría'), 'games');
  await user.click(screen.getByRole('button', { name: 'Buscar' }));
  expect(getAuditLogs).toHaveBeenLastCalledWith({ search: 'games', method: '', page: 1 });
});
