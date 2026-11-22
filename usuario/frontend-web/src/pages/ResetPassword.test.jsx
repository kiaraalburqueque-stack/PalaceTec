import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import '@testing-library/jest-dom/vitest';
import ResetPassword from './ResetPassword';
import ForgotPassword from './ForgotPassword';
import { forgotPassword, resetPassword } from '../services/authService';
vi.mock('../services/authService', () => ({ forgotPassword: vi.fn(), resetPassword: vi.fn() }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });
const renderReset = () => render(<MemoryRouter initialEntries={['/reset-password/abc123']}><Routes><Route path="/reset-password/:token" element={<ResetPassword />} /></Routes></MemoryRouter>);
it('HU55 solicita el enlace por correo con respuesta genérica', async () => {
  forgotPassword.mockResolvedValue({ detail: 'Si el correo está registrado, recibirás un enlace.' });
  const user = userEvent.setup();
  render(<MemoryRouter><ForgotPassword /></MemoryRouter>);
  await user.type(screen.getByLabelText('Correo electrónico'), 'kiara@palacetec.pe');
  await user.click(screen.getByRole('button', { name: 'Enviar enlace' }));
  expect(await screen.findByRole('status')).toHaveTextContent('recibirás un enlace');
  expect(forgotPassword).toHaveBeenCalledWith('kiara@palacetec.pe');
});
it('HU55 envía el token de la URL con la contraseña nueva', async () => {
  resetPassword.mockResolvedValue({ detail: 'Tu contraseña fue actualizada.' });
  const user = userEvent.setup(); renderReset();
  await user.type(screen.getByLabelText('Nueva contraseña'), 'Nueva123!');
  await user.type(screen.getByLabelText('Confirmar contraseña'), 'Nueva123!');
  await user.click(screen.getByRole('button', { name: 'Guardar contraseña' }));
  expect(await screen.findByRole('status')).toHaveTextContent('actualizada');
  expect(resetPassword).toHaveBeenCalledWith('abc123', 'Nueva123!');
});
it('HU55 muestra el rechazo de un token vencido o usado', async () => {
  resetPassword.mockRejectedValue(new Error('El enlace no es válido, ya se usó o venció. Solicita uno nuevo.'));
  const user = userEvent.setup(); renderReset();
  await user.type(screen.getByLabelText('Nueva contraseña'), 'Nueva123!');
  await user.type(screen.getByLabelText('Confirmar contraseña'), 'Nueva123!');
  await user.click(screen.getByRole('button', { name: 'Guardar contraseña' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('venció');
  expect(screen.getByRole('link', { name: 'Solicitar un enlace nuevo' })).toBeInTheDocument();
});
