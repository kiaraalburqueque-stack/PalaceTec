
import { useState } from 'react';
import {
  validatePassword,
  getPasswordStrength,
} from '../utils/passwordValidation';

export default function PasswordStrength({ value, onChange, embedded = false }) {
  const [localPassword, setPassword] = useState('');
  const password = value ?? localPassword;


const validation = validatePassword(password);

const requirements = [
  { label: 'Mínimo 8 caracteres', valid: validation.minLength },
  { label: 'Al menos una letra mayúscula', valid: validation.hasUppercase },
  { label: 'Al menos un símbolo especial', valid: validation.hasSymbol },
];

const strength = getPasswordStrength(password);

  const labels = ['Muy débil', 'Débil', 'Media', 'Fuerte'];
  const colors = ['#dc2626', '#ef4444', '#eab308', '#16a34a'];

  return (
    <div className={embedded ? 'password-field' : 'password-container'}>
      {!embedded && <><h2>Crear cuenta en PalaceTec</h2><p>Ingresa una contraseña segura para tu cuenta.</p></>}

      <label htmlFor="password">Contraseña</label>
      <input
        id="password"
        type="password"
        value={password}
        onChange={(event) => onChange ? onChange(event.target.value) : setPassword(event.target.value)}
        autoComplete="new-password"
        required
        placeholder="Ingresa tu contraseña"
      />

      <div className="strength-bar">
        <div
          className="strength-progress"
          style={{
            width: `${(strength / requirements.length) * 100}%`,
            backgroundColor: colors[strength],
          }}
        />
      </div>

      <p style={{ color: colors[strength] }}>
        Fortaleza: {password ? labels[strength] : 'Sin evaluar'}
      </p>

      <ul className="requirements">
        {requirements.map((item) => (
          <li key={item.label}>
            {item.valid ? '✓' : '○'} {item.label}
          </li>
        ))}
      </ul>
    </div>
  );
}
