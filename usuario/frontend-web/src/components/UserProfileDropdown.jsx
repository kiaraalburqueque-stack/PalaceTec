
import { useContext, useState } from 'react';
import { logout } from '../services/authService';
import { UserContext } from '../context/UserContext';

export default function UserProfileDropdown({ user }) {
  const [isOpen, setIsOpen] = useState(false);
  const context = useContext(UserContext);
  const [error, setError] = useState('');

  if (!user) return <span className="profile-button">Sin sesión</span>;

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
          {user.avatar_url ? <img src={user.avatar_url} alt="Avatar" width="32" height="32" /> : user.nickname.charAt(0).toUpperCase()}
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
          <a href="/perfil">Editar apodo y avatar</a>
          <button type="button" onClick={async () => {
            try { await logout(); context?.setUser(null); window.location.assign('/login'); }
            catch (err) { setError(err.message); }
          }}>
            Cerrar sesión
          </button>
          {error && <p role="alert">{error}</p>}
        </div>
      )}
    </div>
  );
}
