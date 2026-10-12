
import UserProfileDropdown from './UserProfileDropdown';
import useUser from '../hooks/useUser';

export default function Header() {
  const { user } = useUser();

  return (
    <header className="main-header">
      <div className="header-brand">
        <span className="brand-icon">♛</span>
        <span>PalaceTec</span>
      </div>

      <nav className="header-navigation" aria-label="Navegación principal">
        <span>Casino Virtual</span>
      </nav>

      <UserProfileDropdown user={user} />
    </header>
  );
}
