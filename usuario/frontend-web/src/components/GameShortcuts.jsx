import { Link } from 'react-router-dom';

export default function GameShortcuts() {
  return <section className="game-discovery" aria-labelledby="discover-games"><div className="section-heading"><div><span className="eyebrow">TU ENTRETENIMIENTO, EN UN SOLO LUGAR</span><h2 id="discover-games">Elige tu próxima experiencia</h2></div><span className="virtual-badge">100% créditos virtuales</span></div>
    <div className="discovery-grid">
      <Link to="/deportes" className="discovery-card discovery-sports"><span className="discovery-icon" aria-hidden="true">⚽</span><span className="virtual-badge">SIMULACIÓN</span><h3>Deportes virtuales</h3><p>Elige local, empate o visitante. Cuotas decimales y resultados simulados.</p><strong>Ver encuentros →</strong></Link>
      <Link to="/blackjack" className="discovery-card discovery-blackjack"><span className="discovery-icon" aria-hidden="true">A♠</span><span className="virtual-badge">MESA CLÁSICA</span><h3>Blackjack 21</h3><p>Pide carta o plántate frente a la banca. El blackjack natural paga 3:2.</p><strong>Entrar a la mesa →</strong></Link>
      <Link to="/ruleta" className="discovery-card discovery-roulette"><span className="discovery-icon" aria-hidden="true">◉</span><span className="virtual-badge">EN VIVO</span><h3>Ruleta europea</h3><p>Tu mesa de siempre: plenos, colores, docenas y columnas en tiempo real.</p><strong>Explorar salas →</strong></Link>
    </div>
  </section>;
}
