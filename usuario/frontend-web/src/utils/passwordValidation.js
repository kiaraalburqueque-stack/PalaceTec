
export function validatePassword(password) {
  return {
    minLength: password.length >= 8,
    hasUppercase: /[A-Z]/.test(password),
    hasSymbol: /[^A-Za-z0-9]/.test(password),
  };
}

export function getPasswordStrength(password) {
  const requirements = validatePassword(password);

  return Object.values(requirements).filter(Boolean).length;
}
