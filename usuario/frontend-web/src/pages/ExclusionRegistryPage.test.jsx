import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import { apiRequest } from '../services/api';
import ExclusionRegistryPage from './ExclusionRegistryPage';
vi.mock('../services/api', () => ({ apiRequest: vi.fn() }));
afterEach(() => { cleanup(); vi.resetAllMocks(); });
it('HU25 oculta datos de exclusión a un jugador sin permisos', async () => {
  apiRequest.mockRejectedValue(new Error('403'));
  render(<ExclusionRegistryPage />);
  expect(await screen.findByRole('alert')).toHaveTextContent('Acceso reservado');
  expect(screen.queryByLabelText('DNI')).not.toBeInTheDocument();
});
it('HU25 registra y limpia el DNI después de guardar', async () => {
  apiRequest.mockResolvedValueOnce({ role: 'ADMIN' }).mockResolvedValueOnce({ created: true });
  const user = userEvent.setup(); render(<ExclusionRegistryPage />);
  await user.type(await screen.findByLabelText('DNI'), '00000000');
  await user.type(screen.getByLabelText('Fuente'), 'fixture');
  await user.click(screen.getByRole('button', { name: 'Registrar exclusión' }));
  expect(await screen.findByText('Exclusión registrada.')).toBeInTheDocument();
  expect(screen.getByLabelText('DNI')).toHaveValue('');
});
