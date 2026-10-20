import { Navigate } from 'react-router-dom';
import useUser from '../hooks/useUser';
import DailyLimit from '../components/DailyLimit';
export default function ResponsiblePage() {
  const { user, loading } = useUser();
  if (loading) return <p role="status">Consultando sesión...</p>;
  if (!user) return <Navigate to="/login" replace />;
  return <main className="main-sections"><h1>Juego responsable</h1><DailyLimit /></main>;
}
