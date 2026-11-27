
import { useContext, useState } from 'react';
import { logout } from '../services/authService';
import { UserContext } from '../context/UserContext';
import { avatarSrc } from '../utils/avatar';
import Icon from './Icon';

export default function UserProfileDropdown({ user, wallet }) {
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
          {user.avatar_url ? <img src={avatarSrc(user.avatar_url)} alt="Avatar" width="32" height="32" /> : user.nickname.charAt(0).toUpperCase()}
        </span>
        <span className="profile-name">{user.nickname}</span>
        <Icon name="chevron" size={16} className={`profile-chevron ${isOpen ? 'is-open' : ''}`} />
      </button>

      {isOpen && (
        <div
          id="profile-menu"
          className="profile-menu"
        >
          <p className="profile-menu-title"><strong>Mi perfil</strong></p>
          <p>Nombre: {user.name}</p>
          <p className="profile-menu-email">Correo: {user.email}</p>
          {wallet && <div className="profile-wallet"><p>Saldo disponible: <strong>{Number(wallet.balance).toLocaleString('en-US', { maximumFractionDigits: 2 })} CRD</strong></p><small>Bono: {wallet.bonus_balance} CRD · Ganancias: {wallet.winnings_balance} CRD</small></div>}
          <hr />
          <a href="/perfil">Editar apodo y avatar</a>
          <a href="/perfil/seguridad">Cambiar contraseña</a>
          <a href="/perfil/notificaciones">Preferencias de notificación</a>
          {context?.role === 'ADMIN' && <a href="/administracion/exclusiones">Administrar exclusiones</a>}
          <button type="button" className="profile-logout" onClick={async () => {
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
