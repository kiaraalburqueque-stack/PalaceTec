
import { useState } from 'react';

export default function PasswordStrength() {
  const [password, setPassword] = useState('');

  const requirements = [
    { label: 'Mínimo 8 caracteres', valid: password.length >= 8 },
    { label: 'Al menos una letra mayúscula', valid: /[A-Z]/.test(password) },
    { label: 'Al menos un símbolo especial', valid: /[^A-Za-z0-9]/.test(password) },
  ];

  const strength = requirements.filter((item) => item.valid).length;

  const labels = ['Muy débil', 'Débil', 'Media', 'Fuerte'];
  const colors = ['#dc2626', '#ef4444', '#eab308', '#16a34a'];

  return (
    <div className="password-container">
      <h2>Crear cuenta en PalaceTec</h2>
      <p>Ingresa una contraseña segura para tu cuenta.</p>

      <label htmlFor="password">Contraseña</label>
      <input
        id="password"
        type="password"
        value={password}
        onChange={(event) => setPassword(event.target.value)}
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
