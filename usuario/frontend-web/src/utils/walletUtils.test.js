
import { describe, it, expect } from 'vitest';
import {
  formatCredits,
  calculateAvailableBalance,
} from './walletUtils';

describe('HU07 - Saldo disponible', () => {
  it('formatea 1000 créditos correctamente', () => {
    expect(formatCredits(1000)).toBe('1,000 CRD');
  });

  it('formatea cero créditos correctamente', () => {
    expect(formatCredits(0)).toBe('0 CRD');
  });

  it('suma el bono y los créditos ganados', () => {
    expect(calculateAvailableBalance(1000, 500)).toBe(1500);
  });

  it('calcula el saldo cuando no hay bono', () => {
    expect(calculateAvailableBalance(0, 300)).toBe(300);
  });

  it('evita que los saldos negativos reduzcan el total', () => {
    expect(calculateAvailableBalance(-100, 500)).toBe(500);
  });

  it('maneja valores no válidos sin producir NaN', () => {
    expect(calculateAvailableBalance(undefined, null)).toBe(0);
  });
});
