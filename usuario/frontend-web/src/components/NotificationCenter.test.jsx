import { it, expect, vi, afterEach } from 'vitest';
import { act, render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import '@testing-library/jest-dom/vitest';
import NotificationCenter from './NotificationCenter';
import { connectNotifications, getNotifications, markAllNotificationsRead } from '../services/notificationService';
vi.mock('../services/notificationService', () => ({ getNotifications: vi.fn(), connectNotifications: vi.fn(), markAllNotificationsRead: vi.fn(), markNotificationRead: vi.fn() }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });
const item = (id, read = false) => ({ id, category: 'GAME', title: `Aviso ${id}`, message: 'Ronda liquidada', is_read: read, created_at: new Date().toISOString() });
function setup() {
  let push;
  getNotifications.mockResolvedValue({ unread_count: 2, results: [item(2), item(1)] });
  connectNotifications.mockImplementation((handler) => { push = handler; return () => {}; });
  markAllNotificationsRead.mockResolvedValue({ unread_count: 0 });
  render(<MemoryRouter><NotificationCenter userId={1} /></MemoryRouter>);
  return () => push;
}
it('HU57 muestra el contador de no leídas y la lista desplegable', async () => {
  setup();
  const user = userEvent.setup();
  const bell = await screen.findByRole('button', { name: 'Notificaciones: 2 sin leer' });
  await user.click(bell);
  expect(screen.getByRole('region', { name: 'Centro de notificaciones' })).toHaveTextContent('Aviso 2');
});
it('HU57 recibe notificaciones nuevas en tiempo real', async () => {
  const pusher = setup();
  await screen.findByRole('button', { name: 'Notificaciones: 2 sin leer' });
  act(() => pusher()({ event: 'notification:new', data: { ...item(3), unread_count: 3 } }));
  expect(screen.getByRole('button', { name: 'Notificaciones: 3 sin leer' })).toBeInTheDocument();
});
it('HU57 el contador baja al marcar todas como leídas', async () => {
  setup();
  const user = userEvent.setup();
  await user.click(await screen.findByRole('button', { name: 'Notificaciones: 2 sin leer' }));
  await user.click(screen.getByRole('button', { name: 'Marcar todas como leídas' }));
  expect(screen.getByRole('button', { name: 'Notificaciones' })).toBeInTheDocument();
  expect(markAllNotificationsRead).toHaveBeenCalled();
});
