
const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8000';

export async function getCasinoRooms() {
  const response = await fetch(`${API_BASE_URL}/api/v1/rooms`, {
    method: 'GET',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
    },
  });

  if (response.status === 401 || response.status === 403) {
    throw new Error('Debes iniciar sesión para consultar las salas.');
  }

  if (!response.ok) {
    throw new Error('No se pudieron cargar las salas de casino.');
  }

  return response.json();
}
