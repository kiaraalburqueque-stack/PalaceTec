
import { Routes, Route, NavLink } from 'react-router-dom';

import Header from './components/Header';
import UserProvider from './context/UserProvider';

import HomePage from './pages/HomePage';
import RoulettePage from './pages/RoulettePage';
import RegisterPage from './pages/RegisterPage';
import LoginPage from './pages/LoginPage';
import ProfilePage from './pages/ProfilePage';
import ExclusionRegistryPage from './pages/ExclusionRegistryPage';
import ResponsiblePage from './pages/ResponsiblePage';
import SessionTools from './components/SessionTools';
import HistoryPage from './pages/HistoryPage';
import PasswordPage from './pages/PasswordPage';
import AdminGate from './components/AdminGate';
import StatsPage from './pages/StatsPage';
import BlackjackPage from './pages/BlackjackPage';
import SportsPage from './pages/SportsPage';
import ForgotPassword from './pages/ForgotPassword';
import ResetPassword from './pages/ResetPassword';
import NotMePage from './pages/NotMePage';
import NotificationSettings from './pages/NotificationSettings';
import AssistantChat from './components/AssistantChat';
import Icon, { BrandMark } from './components/Icon';

import './App.css';

function App() {
  return (
    <UserProvider>
      <SessionTools />
      <div className="app-layout">
        <Header />

        <nav className="app-page-navigation" aria-label="Páginas de PalaceTec">
          <div className="nav-inner">
            <div className="nav-group">
              <NavLink to="/" end><Icon name="home" />Inicio</NavLink>
              <NavLink to="/ruleta"><Icon name="roulette" />Ruleta</NavLink>
              <NavLink to="/blackjack"><Icon name="cards" />Blackjack</NavLink>
              <NavLink to="/deportes"><Icon name="ball" />Deportes virtuales</NavLink>
            </div>
            <div className="nav-group">
              <NavLink to="/historial"><Icon name="history" />Mi historial</NavLink>
              <NavLink to="/estadisticas"><Icon name="stats" />Mis estadísticas</NavLink>
              <NavLink to="/juego-responsable" className="nav-responsible"><Icon name="shield" />Mis límites</NavLink>
            </div>
            <div className="nav-group nav-auth">
              <NavLink to="/registro"><Icon name="userPlus" />Registro</NavLink>
              <NavLink to="/login"><Icon name="login" />Iniciar sesión</NavLink>
            </div>
          </div>
        </nav>

        <div className="app-content">
          <Routes>
            <Route path="/deportes" element={<SportsPage />} />
            <Route path="/blackjack" element={<BlackjackPage />} />
            <Route path="/" element={<HomePage />} />
            <Route path="/ruleta" element={<RoulettePage />} />
            <Route path="/ruleta/:roomId" element={<RoulettePage />} />
            <Route path="/registro" element={<RegisterPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/forgot-password" element={<ForgotPassword />} />
            <Route path="/reset-password/:token" element={<ResetPassword />} />
            <Route path="/seguridad/no-fui-yo" element={<NotMePage />} />
            <Route path="/seguridad/no-fui-yo/:token" element={<NotMePage />} />
            <Route path="/perfil" element={<ProfilePage />} />
            <Route path="/perfil/seguridad" element={<PasswordPage />} />
            <Route path="/perfil/notificaciones" element={<NotificationSettings />} />
            <Route path="/juego-responsable" element={<ResponsiblePage />} />
            <Route path="/historial" element={<HistoryPage />} />
            <Route path="/estadisticas" element={<StatsPage />} />
            <Route path="/administracion/exclusiones" element={<AdminGate><ExclusionRegistryPage /></AdminGate>} />
            <Route path="*" element={<HomePage />} />
          </Routes>
        </div>
        <footer className="app-footer">
          <div className="footer-inner">
            <div className="footer-brand">
              <span className="footer-logo"><BrandMark size={28} />PalaceTec · Casino virtual</span>
              <span>Créditos simulados. Sin dinero real. Solo mayores de 18 años.</span>
            </div>
            <div className="footer-responsible">
              <span className="age-badge" aria-hidden="true">18+</span>
              <p>Juega con responsabilidad. Define tu límite diario, toma pausas y, si lo necesitas, solicita la autoexclusión.</p>
              <NavLink to="/juego-responsable">Herramientas de juego responsable</NavLink>
            </div>
          </div>
        </footer>
      </div>
      <AssistantChat />
    </UserProvider>
  );
}

export default App;
