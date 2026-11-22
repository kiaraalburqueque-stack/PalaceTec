import { API_BASE_URL } from '../services/api';

// HU65: las fotos subidas viven en el backend (/media/...); los avatares predeterminados, en el frontend.
export function avatarSrc(url) {
  return url && url.startsWith('/media/') ? API_BASE_URL + url : url;
}

export const AVATAR_TYPES = ['image/jpeg', 'image/png', 'image/webp'];
export const AVATAR_MAX_BYTES = 2 * 1024 * 1024;

export function avatarFileError(file) {
  if (!file) return 'Selecciona una imagen.';
  if (!AVATAR_TYPES.includes(file.type)) return 'La foto debe ser JPG, PNG o WEBP.';
  if (file.size > AVATAR_MAX_BYTES) return 'La foto no debe superar 2 MB.';
  return '';
}
