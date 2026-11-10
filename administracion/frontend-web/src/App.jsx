import { NavLink, Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import AdminProvider from './context/AdminProvider';
import { useAdmin } from './context/AdminContext';
import { logout } from './services/adminService';
import Loading from './components/Loading';
import LoginPage from './pages/LoginPage';
import Users from './pages/Users';
import Games from './pages/Games';
import GameForm from './pages/GameForm';
import ResponsibleMonitor from './pages/ResponsibleMonitor';
import Dashboard from './pages/Dashboard';
import './App.css';

// Cada entrada agrega una opción del menú lateral y su ruta.
const SECTIONS = [
  { path: '/', label: 'Dashboard', icon: '📊', element: <Dashboard /> },
  { path: '/usuarios', label: 'Usuarios', icon: '👥', element: <Users /> },
  { path: '/juegos', label: 'Juegos', icon: '🎰', element: <Games /> },
  { path: '/juego-responsable', label: 'Juego responsable', icon: '🛡️', element: <ResponsibleMonitor /> },
];
const EXTRA_ROUTES = [
  { path: '/juegos/nuevo', element: <GameForm /> },
];

function Shell() {
  const { admin, setAdmin, loading } = useAdmin();
  const navigate = useNavigate();
  if (loading) return <Loading label="Verificando sesión..." />;
  if (!admin) return <Routes><Route path="*" element={<LoginPage />} /></Routes>;
  if (admin.role !== 'ADMIN') return <main className="login-screen"><div className="login-card"><h1>Acceso restringido</h1>
    <p role="alert">Tu cuenta ({admin.email}) no tiene permisos de administrador.</p>
    <button className="secondary" onClick={async () => { await logout().catch(() => {}); setAdmin(null); }}>Cambiar de cuenta</button></div></main>;
  const home = SECTIONS[0]?.path === '/' ? null : SECTIONS[0]?.path;
  return <div className="layout">
    <aside className="sidebar">
      <div className="brand"><span>♛</span> PalaceTec <small>Admin</small></div>
      <nav aria-label="Secciones de administración">{SECTIONS.map((section) => <NavLink key={section.path} to={section.path} end={section.path === '/'}>
        <span aria-hidden="true">{section.icon}</span>{section.label}</NavLink>)}</nav>
      <div className="sidebar-footer"><small>{admin.email}</small>
        <button type="button" className="secondary small" onClick={async () => { await logout().catch(() => {}); setAdmin(null); navigate('/'); }}>Cerrar sesión</button></div>
    </aside>
    <main className="content">
      <Routes>
        {SECTIONS.map((section) => <Route key={section.path} path={section.path} element={section.element} />)}
        {EXTRA_ROUTES.map((route) => <Route key={route.path} path={route.path} element={route.element} />)}
        {home && <Route path="/" element={<Navigate to={home} replace />} />}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </main>
  </div>;
}

export default function App() {
  return <AdminProvider><Shell /></AdminProvider>;
}
