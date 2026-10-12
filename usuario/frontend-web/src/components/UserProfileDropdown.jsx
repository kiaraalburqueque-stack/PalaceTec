
import { useState } from 'react';

export default function UserProfileDropdown({ user }) {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <div className="profile-dropdown">
      <button
        type="button"
        className="profile-button"
        aria-expanded={isOpen}
        aria-controls="profile-menu"
        onClick={() => setIsOpen(!isOpen)}
      >
        <span className="profile-avatar">
          {user.nickname.charAt(0).toUpperCase()}
        </span>
        <span>{user.nickname}</span>
        <span>{isOpen ? '▲' : '▼'}</span>
      </button>

      {isOpen && (
        <div
          id="profile-menu"
          className="profile-menu"
        >
          <p><strong>Mi perfil</strong></p>
          <p>Nombre: {user.name}</p>
          <p>Correo: {user.email}</p>
          <hr />
          <button type="button" disabled>
            Configuración (próximamente)
          </button>
          <button type="button" disabled>
            Cerrar sesión (próximamente)
          </button>
        </div>
      )}
    </div>
  );
}
