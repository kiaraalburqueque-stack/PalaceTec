import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import '@testing-library/jest-dom/vitest';
import RegisterPage from './RegisterPage';
import { register } from '../services/authService';
vi.mock('../services/authService', () => ({ register: vi.fn() }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });
async function fill() {
  render(<MemoryRouter><RegisterPage /></MemoryRouter>);
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Correo electrónico'), 'nuevo@example.com');
  await user.type(screen.getByLabelText('DNI'), '12345678');
  await user.type(screen.getByLabelText('Fecha de nacimiento'), '2000-01-01');
  await user.type(screen.getByLabelText('Contraseña'), 'Palace123!');
  return user;
}
describe('HU01 HU05 registro conectado', () => {
  it('envía los cuatro campos y confirma el bono sin inventar sesión', async () => {
    register.mockResolvedValue({ id: 7, email: 'nuevo@example.com' });
    const user = await fill();
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }));
    expect(register).toHaveBeenCalledWith({ email: 'nuevo@example.com', dni: '12345678', birth_date: '2000-01-01', password: 'Palace123!' });
    expect(await screen.findByText(/Recibiste 1,000/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Iniciar sesión' })).toHaveAttribute('href', '/login');
  });
  it('muestra el conflicto de Django y conserva el formulario', async () => {
    register.mockRejectedValue(new Error('email: Este correo ya está registrado.'));
    const user = await fill();
    await user.click(screen.getByRole('button', { name: 'Crear cuenta' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Este correo ya está registrado');
    expect(screen.getByLabelText('Correo electrónico')).toHaveValue('nuevo@example.com');
  });
});
