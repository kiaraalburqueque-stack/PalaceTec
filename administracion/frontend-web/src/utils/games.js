export const CATEGORIES = [['RULETA', 'Ruleta'], ['CARTAS', 'Cartas'], ['DEPORTES', 'Deportes virtuales'], ['OTROS', 'Otros']];
export const categoryLabel = (value) => Object.fromEntries(CATEGORIES)[value] || value;
export const slugify = (text) => text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 50);

// Validación compartida por el alta (HU43) y la edición (HU44).
export function validateGame(values) {
  const errors = {};
  if ('title' in values && values.title.trim().length < 3) errors.title = 'El título necesita al menos 3 caracteres.';
  if ('slug' in values && !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(values.slug)) errors.slug = 'Usa minúsculas, números y guiones.';
  const min = Number(values.min_bet); const max = Number(values.max_bet);
  if (!(min >= 1)) errors.min_bet = 'La apuesta mínima debe ser al menos 1.';
  if (!(max >= 1)) errors.max_bet = 'La apuesta máxima debe ser al menos 1.';
  if (!errors.min_bet && !errors.max_bet && min > max) errors.min_bet = 'La apuesta mínima no puede ser mayor que la máxima.';
  const rtp = Number(values.rtp);
  if (!(rtp >= 80 && rtp <= 99.99)) errors.rtp = 'El RTP debe estar entre 80 y 99.99.';
  return errors;
}

export function serverErrors(data = {}) {
  return Object.fromEntries(Object.entries(data).filter(([key]) => key !== 'detail').map(([key, value]) => [key, Array.isArray(value) ? value.join(' ') : String(value)]));
}
