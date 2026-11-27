import { Navigate } from 'react-router-dom';
import useUser from '../hooks/useUser';
import DailyLimit from '../components/DailyLimit';
import SelfExclusion from '../components/SelfExclusion';
export default function ResponsiblePage() {
  const { user, loading } = useUser();
  if (loading) return <p role="status">Consultando sesión...</p>;
  if (!user) return <Navigate to="/login" replace />;
  return <main className="main-sections responsible-page"><h1>Juego responsable</h1>
    <p className="responsible-intro">Herramientas para jugar con control. PalaceTec usa créditos virtuales, sin dinero real, y es solo para mayores de 18 años. Si sientes que el juego dejó de ser entretenimiento, toma una pausa.</p>
    <DailyLimit /><SelfExclusion /></main>;
}
