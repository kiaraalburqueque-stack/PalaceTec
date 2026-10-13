
const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000';

export async function getCasinoRooms() {
  const response = await fetch(`${API_BASE_URL}/api/v1/rooms`, {
    method: 'GET',
    credentials: 'include',
  });

  if (!response.ok) {
    throw new Error('No se pudieron cargar las salas del casino.');
  }

  const data = await response.json();

  if (!Array.isArray(data)) {
    throw new Error('El servidor devolvió un catálogo de salas inválido.');
  }

  return data;
}
