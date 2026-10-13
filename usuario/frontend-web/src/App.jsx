
import { Routes, Route, NavLink } from 'react-router-dom';

import Header from './components/Header';
import UserProvider from './context/UserProvider';

import HomePage from './pages/HomePage';
import RoulettePage from './pages/RoulettePage';
import RegisterPage from './pages/RegisterPage';
import LoginPage from './pages/LoginPage';
import ProfilePage from './pages/ProfilePage';

import './App.css';

function App() {
  return (
    <UserProvider>
      <div className="app-layout">
        <Header />

        <nav className="app-page-navigation" aria-label="Páginas de PalaceTec">
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
            <Route path="*" element={<HomePage />} />
          </Routes>
        </div>
      </div>
    </UserProvider>
  );
}

export default App;
