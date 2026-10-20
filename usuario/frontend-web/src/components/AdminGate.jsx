import { Navigate } from 'react-router-dom';
import useUser from '../hooks/useUser';
export default function AdminGate({ children }) {
  const { user, loading, role } = useUser();
  if (loading) return <p role="status">Consultando sesión...</p>;
  if (!user) return <Navigate to="/login" replace />;
  if (!role) return <p role="status">Verificando permisos...</p>;
  return role === 'ADMIN' ? children : <main className="main-sections"><h1>Acceso restringido</h1><p role="alert">Esta página requiere permisos de administrador.</p></main>;
}
