
import { describe, it, expect } from 'vitest';
import {
  validatePassword,
  getPasswordStrength,
} from './passwordValidation';

describe('HU03 - Validación de contraseña', () => {
  it('rechaza contraseñas menores a 8 caracteres', () => {
    const result = validatePassword('Hola1!');
    expect(result.minLength).toBe(false);
  });

  it('detecta una letra mayúscula', () => {
    const result = validatePassword('Hola1234');
    expect(result.hasUppercase).toBe(true);
  });

  it('detecta símbolos especiales', () => {
    const result = validatePassword('Hola1234!');
    expect(result.hasSymbol).toBe(true);
  });

  it('identifica una contraseña fuerte', () => {
    expect(getPasswordStrength('Hola1234!')).toBe(3);
  });

  it('identifica una contraseña débil', () => {
    expect(getPasswordStrength('hola')).toBe(0);
  });

  it('detecta contraseñas sin mayúsculas', () => {
    const result = validatePassword('hola1234!');
    expect(result.hasUppercase).toBe(false);
  });
});
