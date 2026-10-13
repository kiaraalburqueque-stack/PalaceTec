
const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000';

export async function getWalletBalance() {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/wallet/balance`,
    {
      method: 'GET',
      credentials: 'include',
    }
  );

  if (!response.ok) {
    throw new Error('No se pudo consultar el saldo del jugador.');
  }

  const data = await response.json();
  const balance = Number(data.balance);

  if (!Number.isFinite(balance) || balance < 0) {
    throw new Error('El servidor devolvió un saldo inválido.');
  }

  return balance;
}

export async function checkPreBet(amount) {
  const csrfResponse = await fetch(
    `${API_BASE_URL}/api/v1/auth/csrf`,
    {
      method: 'GET',
      credentials: 'include',
    }
  );

  if (!csrfResponse.ok) {
    throw new Error('No se pudo verificar la sesión.');
  }

  const { csrfToken } = await csrfResponse.json();

  const response = await fetch(
    `${API_BASE_URL}/api/v1/wallet/pre-bet-check`,
    {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-CSRFToken': csrfToken,
      },
      body: JSON.stringify({ amount }),
    }
  );

  if (!response.ok) {
    throw new Error('No se pudo validar la apuesta.');
  }

  const data = await response.json();

  if (typeof data.can_bet !== 'boolean') {
    throw new Error('El servidor devolvió una validación inválida.');
  }

  return data;
}
