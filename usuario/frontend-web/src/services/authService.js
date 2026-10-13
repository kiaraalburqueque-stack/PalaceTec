
const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8000';

async function readResponse(response) {
  const data = await response.json().catch(() => ({}));

  if (!response.ok) {
    const message =
      typeof data.detail === 'string'
        ? data.detail
        : 'No se pudo completar la solicitud.';

    const error = new Error(message);
    error.status = response.status;
    error.data = data;
    throw error;
  }

  return data;
}

export async function getCsrfToken() {
  const response = await fetch(`${API_BASE_URL}/api/v1/auth/csrf`, {
    method: 'GET',
    credentials: 'include',
  });

  const data = await readResponse(response);
  return data.csrfToken;
}

export async function login(email, password) {
  const csrfToken = await getCsrfToken();

  const response = await fetch(`${API_BASE_URL}/api/v1/auth/login`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-CSRFToken': csrfToken,
    },
    body: JSON.stringify({ email, password }),
  });

  return readResponse(response);
}

export async function getCurrentUser() {
  const response = await fetch(`${API_BASE_URL}/api/v1/auth/me`, {
    method: 'GET',
    credentials: 'include',
  });

  return readResponse(response);
}
export async function getUserProfile() {
  const response = await fetch(
    `${import.meta.env.VITE_API_BASE_URL}/api/v1/users/me`,
    {
      method: 'GET',
      credentials: 'include',
    }
  );

  if (!response.ok) {
    throw new Error('No se pudo obtener el perfil del usuario.');
  }

  return response.json();
}