import { it, expect, vi, afterEach, beforeAll } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import AvatarUpload from './AvatarUpload';
import { uploadAvatar } from '../services/authService';
vi.mock('../services/authService', () => ({ uploadAvatar: vi.fn() }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });
beforeAll(() => { URL.createObjectURL = vi.fn(() => 'blob:vista'); URL.revokeObjectURL = vi.fn(); });
const picker = () => screen.getByLabelText(/Subir mi foto/);

it('HU65 muestra la vista previa y sube la foto válida', async () => {
  const user = userEvent.setup();
  const onUploaded = vi.fn();
  uploadAvatar.mockResolvedValue({ id: 1, email: 'a@b.pe', avatar_url: '/media/avatars/user_1_x.webp' });
  render(<AvatarUpload onUploaded={onUploaded} />);
  const file = new File(['png'], 'foto.png', { type: 'image/png' });
  await user.upload(picker(), file);
  expect(screen.getByAltText('Vista previa de la foto')).toHaveAttribute('src', 'blob:vista');
  await user.click(screen.getByRole('button', { name: 'Usar esta foto' }));
  expect(uploadAvatar).toHaveBeenCalledWith(file);
  expect(onUploaded).toHaveBeenCalledWith(expect.objectContaining({ avatar_url: '/media/avatars/user_1_x.webp' }));
  expect(await screen.findByText('Foto actualizada.')).toBeInTheDocument();
});

it('HU65 rechaza otro formato o más de 2 MB con un mensaje claro', async () => {
  const user = userEvent.setup({ applyAccept: false });
  render(<AvatarUpload onUploaded={vi.fn()} />);
  await user.upload(picker(), new File(['gif'], 'foto.gif', { type: 'image/gif' }));
  expect(screen.getByRole('alert')).toHaveTextContent('JPG, PNG o WEBP');
  const heavy = new File([new Uint8Array(2 * 1024 * 1024 + 1)], 'grande.jpg', { type: 'image/jpeg' });
  await user.upload(picker(), heavy);
  expect(screen.getByRole('alert')).toHaveTextContent('2 MB');
  expect(screen.queryByRole('button', { name: 'Usar esta foto' })).not.toBeInTheDocument();
  expect(uploadAvatar).not.toHaveBeenCalled();
});

it('HU65 muestra el error del servidor', async () => {
  const user = userEvent.setup();
  uploadAvatar.mockRejectedValue(new Error('El contenido del archivo no corresponde a una imagen válida.'));
  render(<AvatarUpload onUploaded={vi.fn()} />);
  await user.upload(picker(), new File(['x'], 'foto.webp', { type: 'image/webp' }));
  await user.click(screen.getByRole('button', { name: 'Usar esta foto' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('imagen válida');
});
