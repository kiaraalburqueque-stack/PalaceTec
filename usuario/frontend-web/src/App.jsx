
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

import './App.css';

function App() {
  return (
    <UserProvider>
      <SessionTools />
      <div className="app-layout">
        <Header />

        <nav className="app-page-navigation" aria-label="Páginas de PalaceTec">
          <NavLink to="/juego-responsable">Mis límites</NavLink>
          <NavLink to="/historial">Mi historial</NavLink>
          <NavLink to="/" end>
            Inicio
          </NavLink>

          <NavLink to="/ruleta">
            Ruleta
          </NavLink>

          <NavLink to="/registro">
            Registro
          </NavLink>

          <NavLink to="/login">
            Iniciar sesión
          </NavLink>
        </nav>

        <div className="app-content">
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/ruleta" element={<RoulettePage />} />
            <Route path="/ruleta/:roomId" element={<RoulettePage />} />
            <Route path="/registro" element={<RegisterPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/perfil" element={<ProfilePage />} />
            <Route path="/perfil/seguridad" element={<PasswordPage />} />
            <Route path="/juego-responsable" element={<ResponsiblePage />} />
            <Route path="/historial" element={<HistoryPage />} />
            <Route path="/administracion/exclusiones" element={<AdminGate><ExclusionRegistryPage /></AdminGate>} />
            <Route path="*" element={<HomePage />} />
          </Routes>
        </div>
        <footer className="app-footer"><span>♛ PalaceTec · Casino virtual</span><span>Créditos simulados. Sin dinero real. Solo mayores de 18 años.</span></footer>
      </div>
    </UserProvider>
  );
}

export default App;
