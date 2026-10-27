
import GameCatalog from '../components/GameCatalog';
import GameShortcuts from '../components/GameShortcuts';
import useUser from '../hooks/useUser';
import { Link } from 'react-router-dom';

export default function HomePage() {
  const { user, loading } = useUser();
  return (
    <main className="casino-home">
      <section className="casino-hero">
        <div className="hero-copy"><span className="eyebrow">PALACETEC · CASINO VIRTUAL</span><h1>Tu próxima jugada<br /><em>empieza aquí.</em></h1><p>Ruleta europea en tiempo real. Vive cada ronda con créditos virtuales y juega a tu manera.</p><div className="hero-actions"><Link className="primary-action" to={user ? '#salas' : '/registro'} onClick={user ? (event) => { event.preventDefault(); document.getElementById('salas')?.scrollIntoView({ behavior: 'smooth' }); } : undefined}>{user ? 'Explorar salas' : 'Crear mi cuenta'}</Link><span>1,000 créditos de bienvenida</span></div><small>Sin dinero real · Solo mayores de 18 años</small></div>
        <div className="hero-art" aria-hidden="true"><div className="roulette-illustration"><span>0</span><div className="wheel-center">P<span>EUROPEAN ROULETTE</span></div></div><div className="floating-chip chip-one">100</div><div className="floating-chip chip-two">50</div><div className="live-tag"><i /> MESAS EN VIVO</div></div>
      </section>
      <div className="casino-features"><span>◉ Ruleta europea</span><span>↗ Saldo en tiempo real</span><span>♧ Créditos virtuales</span><span>✓ Sesión protegida</span></div>
      <div id="salas" />
      <GameShortcuts />
      {loading ? <p role="status">Consultando sesión...</p> : user ? <GameCatalog /> : <section className="main-sections"><h1>Bienvenido a PalaceTec</h1><p>Crea tu cuenta y recibe 1,000 créditos virtuales.</p><Link to="/registro">Crear cuenta</Link> · <Link to="/login">Iniciar sesión</Link></section>}
    </main>
  );
}
