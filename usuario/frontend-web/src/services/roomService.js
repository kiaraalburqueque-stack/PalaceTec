
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

export async function getRoomState(roomId) {
  const response = await fetch(
    `${import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000'}/api/v1/rooms/${roomId}/state`,
    {
      method: 'GET',
      credentials: 'include',
    }
  );

  if (!response.ok) {
    throw new Error('No se pudo consultar el estado de la sala.');
  }

  const data = await response.json();

  if (!data || Number(data.room_id) !== Number(roomId)) {
    throw new Error('El servidor devolvió una sala inválida.');
  }

  return data;
}

export async function placeRouletteBet(roomId, betData) {
  const API_BASE_URL =
    import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000';

  const csrfToken = document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith('csrftoken='))
    ?.split('=')[1];

  const response = await fetch(
    `${API_BASE_URL}/api/v1/rooms/${roomId}/bets`,
    {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        ...(csrfToken
          ? { 'X-CSRFToken': decodeURIComponent(csrfToken) }
          : {}),
      },
      body: JSON.stringify(betData),
    }
  );

  const result = await response.json();

  if (!response.ok) {
    const error = new Error(
      typeof result.detail === 'string'
        ? result.detail
        : 'No se pudo registrar la apuesta.'
    );

    error.code = result.code || 'bet_error';

    throw error;
  }

  return result;
}
