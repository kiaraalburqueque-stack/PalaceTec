import { test, expect } from '@playwright/test';

// Recorrido del jugador contra Spring (8080), con runroulette abriendo y liquidando rondas reales de 15 segundos.
test('spring: registro, login, apuesta en ruleta, liquidación en vivo, historial, límite y notificación', async ({ page }) => {
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  const suffix = Date.now();
  const email = `spring-${suffix}@example.com`;
  const password = 'Palace123!';

  // Registro y login
  await page.goto('/registro');
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByLabel('DNI', { exact: true }).fill(String(suffix).slice(-8));
  await page.getByLabel('Fecha de nacimiento').fill('2000-01-01');
  await page.getByLabel('Contraseña', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Crear cuenta', exact: true }).click();
  await expect(page.getByText('¡Cuenta creada!')).toBeVisible();
  await page.goto('/login');
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByLabel('Contraseña', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Iniciar sesión', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Juegos disponibles' })).toBeVisible();

  // Límite diario de 50 CRD
  await page.getByRole('link', { name: 'Mis límites', exact: true }).click();
  await page.getByLabel('Límite diario en CRD').fill('50');
  await page.getByRole('button', { name: 'Guardar límite' }).click();
  await expect(page.getByText(/Disponible: 50.00 CRD/)).toBeVisible();

  // Mesa en vivo: el WebSocket de Spring entrega el estado y el temporizador
  await page.goto('/ruleta/1');
  await expect(page.getByText('Conexión: En vivo')).toBeVisible();
  const seconds = async () => Number((await page.getByRole('timer', { name: 'Tiempo para apostar' }).textContent()).match(/\d+/)[0]);
  // Cuatro apuestas caben con holgura si la ronda acaba de abrirse (15 s): se espera una ronda nueva.
  await expect.poll(seconds, { timeout: 40000 }).toBeGreaterThanOrEqual(12);

  // Cubren todos los números: docenas 1, 2 y 3 y el pleno 0. Siempre gana alguna, así que hay premio y aviso.
  const bet = async (kind, selection, label) => {
    await page.getByLabel('Tipo de apuesta:').selectOption(kind);
    if (selection) await page.getByLabel(label).selectOption(selection);
    await page.getByRole('button', { name: 'Apostar', exact: true }).click();
  };
  const rows = page.locator('.history-panel tbody tr');
  await bet('DOZEN', '1', 'Docena o columna');
  await expect(rows).toHaveCount(1);
  await bet('DOZEN', '2', 'Docena o columna');
  await expect(rows).toHaveCount(2);
  await bet('DOZEN', '3', 'Docena o columna');
  await expect(rows).toHaveCount(3);
  await bet('STRAIGHT_UP', '0', 'Selecciona un número:');
  await expect(rows).toHaveCount(4);
  await expect(page.getByText('¡Apuesta registrada correctamente!')).toBeVisible();

  // Límite: quedan 10 CRD y una ficha de 50 se rechaza
  await page.getByRole('button', { name: '50', exact: true }).click();
  await page.getByRole('button', { name: 'Apostar', exact: true }).click();
  await expect(page.getByRole('alert').filter({ hasText: /l[ií]mite diario/i }).first()).toBeVisible();
  await expect(rows).toHaveCount(4);

  // Liquidación en vivo, sin recargar: llega el resultado de la ronda, el premio y el saldo
  await expect(page.getByRole('dialog', { name: 'Resultado de tu ronda' })).toBeVisible({ timeout: 60000 });
  await expect(page.getByRole('list', { name: 'Resultados recientes' })).toBeVisible();
  await page.getByRole('button', { name: 'Cerrar resultado' }).click();
  await expect(rows.first().locator('td').last()).not.toHaveText('Pendiente');

  // Notificación: llega por el WebSocket personal
  await expect(page.getByRole('button', { name: /Notificaciones: \d+ sin leer/ })).toBeVisible();
  await page.getByRole('button', { name: /Notificaciones/ }).click();
  await expect(page.getByRole('region', { name: 'Centro de notificaciones' }).getByText('¡Ganaste en la ruleta!')).toBeVisible();

  // Historial
  await page.getByRole('link', { name: 'Mi historial', exact: true }).click();
  await expect(page.getByText('4 apuestas · Página 1')).toBeVisible();
  await expect(page.getByRole('cell', { name: 'Ganada', exact: true })).toHaveCount(1);
  await expect(page.getByRole('cell', { name: 'Pendiente', exact: true })).toHaveCount(0);

  expect(errors).toEqual([]);
});
