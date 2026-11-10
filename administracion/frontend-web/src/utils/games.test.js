import { it, expect } from 'vitest';
import { slugify, validateGame } from './games';
const valid = { title: 'Ruleta Imperial', slug: 'ruleta-imperial', min_bet: '10', max_bet: '500', rtp: '97.30' };
it('HU43 genera un slug válido a partir del título', () => {
  expect(slugify('Ruleta Única 2026!')).toBe('ruleta-unica-2026');
});
it('HU43 y HU44 rechazan min_bet mayor que max_bet, RTP fuera de rango y slug inválido', () => {
  expect(validateGame(valid)).toEqual({});
  expect(validateGame({ ...valid, min_bet: '900' }).min_bet).toMatch(/mayor que la máxima/);
  expect(validateGame({ ...valid, rtp: '120' }).rtp).toBeDefined();
  expect(validateGame({ ...valid, slug: 'Con Espacios' }).slug).toBeDefined();
});
