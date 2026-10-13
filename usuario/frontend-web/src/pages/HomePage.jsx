
import CasinoRoomCatalog from '../components/CasinoRoomCatalog';
import useUser from '../hooks/useUser';
import { Link } from 'react-router-dom';

export default function HomePage() {
  const { user, loading } = useUser();
  return (
    <main>
      {loading ? <p role="status">Consultando sesión...</p> : user ? <CasinoRoomCatalog /> : <section className="main-sections"><h1>Bienvenido a PalaceTec</h1><p>Crea tu cuenta y recibe 1,000 créditos virtuales.</p><Link to="/registro">Crear cuenta</Link> · <Link to="/login">Iniciar sesión</Link></section>}
    </main>
  );
}
