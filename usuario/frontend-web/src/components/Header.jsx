import UserProfileDropdown from './UserProfileDropdown';

export default function Header({ user }) {
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
