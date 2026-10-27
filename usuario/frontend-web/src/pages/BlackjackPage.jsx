import { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import useUser from '../hooks/useUser';
import useGameWallet from '../hooks/useGameWallet';
import { getBlackjack, startBlackjack, actBlackjack } from '../services/casinoService';

const suits = { S: '♠', H: '♥', D: '♦', C: '♣' };
const outcomes = { BLACKJACK: '¡Blackjack natural!', WON: '¡Ganaste la mano!', PUSH: 'Empate: recuperas tu apuesta', LOST: 'La banca gana esta mano' };

function Cards({ cards, hidden }) {
  return <div className="playing-cards">{cards.map((card, index) => {
    const [rank, suit] = card.split('_');
    return <div className={`playing-card ${suit === 'H' || suit === 'D' ? 'red-card' : ''}`} key={`${card}-${index}`} aria-label={`${rank} ${suits[suit]}`}><span>{rank}<small>{suits[suit]}</small></span><b aria-hidden="true">{suits[suit]}</b></div>;
  })}{hidden && <div className="playing-card card-back" aria-label="Carta oculta de la banca"><b>♛</b></div>}</div>;
}

export default function BlackjackPage() {
  const { user, loading } = useUser();
  const { wallet, refresh } = useGameWallet(user);
  const [hand, setHand] = useState(null);
  const [ready, setReady] = useState(false);
  const [busy, setBusy] = useState(false);
  const [amount, setAmount] = useState('10');
  const [error, setError] = useState('');
  const [pending, setPending] = useState(null);
  useEffect(() => {
    if (!user) return;
    let active = true;
    getBlackjack().then(data => { if (active) { setHand(data.hand); setError(''); } })
      .catch(err => { if (active) setError(err.message); }).finally(() => { if (active) setReady(true); });
    return () => { active = false; };
  }, [user]);
  async function deal() {
    if (busy) return;
    setBusy(true); setError('');
    const payload = pending || { amount, reference: crypto.randomUUID() };
    setPending(payload);
    try { const data = await startBlackjack(payload); setHand(data.hand); setPending(null); await refresh(); }
    catch (err) { setError(err.message); if (err.status) setPending(null); }
    finally { setBusy(false); }
  }
  async function act(action) {
    if (busy) return;
    setBusy(true); setError('');
    try { const data = await actBlackjack(hand.id, { action, revision: hand.revision }); setHand(data.hand); await refresh(); }
    catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }
  if (loading) return <p role="status">Consultando sesión...</p>;
  if (!user) return <Navigate to="/login" replace />;
  return <main className="games-page"><div className="game-page-heading"><div><span className="eyebrow">PALACETEC CASINO</span><h1>Blackjack <em>21</em></h1><p>Acércate a 21 sin pasarte. Tú decides cuándo plantarte.</p></div><div className="game-balance"><small>SALDO DISPONIBLE</small><strong>{wallet ? `${wallet.balance} CRD` : 'Consultando…'}</strong><Link to="/juego-responsable">Mis límites →</Link></div></div>
    <div className="blackjack-layout"><section className="blackjack-table" aria-label="Mesa de blackjack"><div className="table-label">BLACKJACK PAGA 3:2 · BANCA SE PLANTA EN 17</div>
      <div className="hand-zone"><h2>Banca <span>{hand ? `${hand.dealer_score}${hand.dealer_hidden ? ' + ?' : ''}` : '—'}</span></h2>{hand ? <Cards cards={hand.dealer} hidden={hand.dealer_hidden} /> : <div className="empty-hand">La banca espera tu jugada</div>}</div>
      <div className="table-medallion" aria-hidden="true">♛<small>PALACETEC</small></div>
      <div className="hand-zone"><h2>Tu mano <span>{hand?.player_score ?? '—'}</span></h2>{hand ? <Cards cards={hand.player} /> : <div className="empty-hand">Tus cartas aparecerán aquí</div>}</div>
      {hand && !hand.active && <div className={`hand-result ${hand.outcome === 'LOST' ? 'result-lost' : ''}`} role="status"><strong>{outcomes[hand.outcome]}</strong><span>Apuesta: {hand.amount} CRD · Pago total: {hand.payout} CRD</span></div>}
    </section><aside className="game-slip"><h2>Tu jugada</h2><p className="muted">Una mano contra la banca · 10 a 500 CRD</p>
      {error && <p role="alert" className="game-error">{error}</p>}
      {!ready && <p role="status">Recuperando tu mesa...</p>}
      {hand?.active ? <><p className="stake-summary">En juego <strong>{hand.amount} CRD</strong></p><button className="primary-action" disabled={busy} onClick={() => act('hit')}>Pedir carta</button><button className="secondary-game-action" disabled={busy} onClick={() => act('stand')}>Plantarse</button></> : <><label htmlFor="blackjack-amount">Importe en créditos</label><input id="blackjack-amount" type="number" min="10" max="500" step="0.01" value={amount} disabled={busy || Boolean(pending)} onChange={event => setAmount(event.target.value)} /><div className="quick-stakes">{[10, 50, 100, 500].map(value => <button disabled={busy || Boolean(pending)} key={value} onClick={() => setAmount(String(value))}>{value}</button>)}</div><button className="primary-action" disabled={busy || !ready || !amount || Number(amount) < 10 || Number(amount) > 500} onClick={deal}>{busy ? 'Repartiendo…' : pending ? 'Reintentar misma mano' : 'Repartir cartas'}</button></>}
      <details className="game-rules" open><summary>Reglas de la mesa</summary><p>As: 1 u 11. Figuras: 10. La banca se planta en 17, incluido 17 suave. Victoria: devolución ×2; natural: ×2.5; empate: ×1.</p><p>Sin dividir, doblar ni seguro. Una mano sin actividad se planta automáticamente a los 10 minutos. Solo créditos virtuales.</p></details><Link to="/historial">Ver mis jugadas →</Link>
    </aside></div>
  </main>;
}
