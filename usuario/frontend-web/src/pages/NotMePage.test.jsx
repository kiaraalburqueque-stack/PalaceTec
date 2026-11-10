import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import '@testing-library/jest-dom/vitest';
import NotMePage from './NotMePage';
import { reportNotMe } from '../services/authService';
vi.mock('../services/authService', () => ({ reportNotMe: vi.fn() }));
vi.mock('../services/api', () => ({ endSession: vi.fn() }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });
it('HU56 bloquea la cuenta con el token del correo', async () => {
  reportNotMe.mockResolvedValue({ detail: 'Bloqueamos tu cuenta temporalmente.' });
  const user = userEvent.setup();
  render(<MemoryRouter initialEntries={['/seguridad/no-fui-yo?token=firmado']}><NotMePage /></MemoryRouter>);
  await user.click(screen.getByRole('button', { name: /No fui yo/ }));
  expect(reportNotMe).toHaveBeenCalledWith('firmado');
  expect(await screen.findByRole('status')).toHaveTextContent('Bloqueamos tu cuenta');
});
it('HU56 no permite confirmar sin token', () => {
  render(<MemoryRouter initialEntries={['/seguridad/no-fui-yo']}><NotMePage /></MemoryRouter>);
  expect(screen.getByRole('button', { name: /No fui yo/ })).toBeDisabled();
});
