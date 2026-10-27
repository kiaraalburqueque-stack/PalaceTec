import { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import useUser from '../hooks/useUser';
import useGameWallet from '../hooks/useGameWallet';
import { getSportEvents, getSportTickets, placeSportTicket } from '../services/casinoService';

const labels = { HOME: 'Local', DRAW: 'Empate', AWAY: 'Visitante' };

export default function SportsPage() {
  const { user, loading } = useUser();
  const { wallet, refresh } = useGameWallet(user);
  const [snapshot, setSnapshot] = useState(null);
  const [tickets, setTickets] = useState([]);
  const [selected, setSelected] = useState(null);
  const [amount, setAmount] = useState('10');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [now, setNow] = useState(() => Date.now());
  const [pending, setPending] = useState(null);
  useEffect(() => {
    if (!user) return;
    let active = true;
    async function sync() {
      try {
        const [events, bets] = await Promise.all([getSportEvents(), getSportTickets()]);
        if (active) { setSnapshot({ ...events, offset: Date.parse(events.server_time) - Date.now() }); setTickets(bets); }
      } catch (err) { if (active) setError(err.message); }
    }
    void sync();
    const poll = setInterval(sync, 3000);
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => { active = false; clearInterval(poll); clearInterval(timer); };
  }, [user]);
  const event = snapshot?.events.find(item => item.id === selected?.event.id);
  const marketOpen = event && event.phase === 'OPEN' && Date.parse(event.starts_at) > now + (snapshot?.offset || 0);
  const potential = Number(amount) * Number(selected?.odds || 0);
  async function submit() {
    if (busy || !selected) return;
    setBusy(true); setError(''); setMessage('');
    const payload = pending || { event_id: selected.event.id, selection: selected.side, quoted_odds: selected.odds, amount, reference: crypto.randomUUID() };
    setPending(payload);
    try {
      await placeSportTicket(payload); setPending(null);
      setMessage('Boleto registrado. El resultado se liquidará automáticamente.');
      setTickets(await getSportTickets()); await refresh();
    } catch (err) { setError(err.message); if (err.status) setPending(null); }
    finally { setBusy(false); }
  }
  if (loading) return <p role="status">Consultando sesión...</p>;
  if (!user) return <Navigate to="/login" replace />;
  return <main className="games-page"><div className="sports-hero"><span className="virtual-badge">DEPORTES VIRTUALES · DEMO</span><h1>La emoción del fútbol.<br /><em>Tu pronóstico, tu jugada.</em></h1><p>Encuentros ficticios, cuotas de demostración y resultados simulados. No son partidos reales. Sin dinero real.</p><span className="sports-hero-ball" aria-hidden="true">⚽</span></div>
    <div className="sports-layout"><section><div className="section-heading"><h2>Fútbol simulado <span className="event-count">{snapshot?.events.length ?? '…'}</span></h2><span className="muted">Mercado 1 · X · 2</span></div>
      {!snapshot && <p role="status">Cargando encuentros...</p>}
      {snapshot?.events.length === 0 && <p>El calendario se prepara automáticamente. Vuelve a consultar en unos segundos.</p>}
      <div className="sports-event-list">{snapshot?.events.map(item => {
        const seconds = Math.max(0, Math.ceil((Date.parse(item.starts_at)-now-snapshot.offset)/1000));
        const open = item.phase === 'OPEN' && seconds > 0;
        return <article className="sports-event" key={item.id}><div className="event-topline"><span>LIGA PALACETEC · SIMULACIÓN</span><strong>{item.phase === 'SETTLED' ? 'FINALIZADO' : open ? `Cierra en ${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}` : 'EN JUEGO SIMULADO'}</strong></div><div className="event-teams"><div><span>{item.home}</span><span>{item.away}</span></div><b>{item.score ? `${item.score[0]} – ${item.score[1]}` : 'VS'}</b></div><div className="market-buttons">{['HOME', 'DRAW', 'AWAY'].map((side, index) => <button key={side} disabled={!open || busy || Boolean(pending)} aria-pressed={selected?.event.id === item.id && selected.side === side} aria-label={`${side === 'HOME' ? item.home : side === 'AWAY' ? item.away : 'Empate'} cuota ${item.odds[side]}`} onClick={() => { setSelected({ event: item, side, odds: item.odds[side] }); setError(''); setMessage(''); }}><span>{['1', 'X', '2'][index]} · {labels[side]}</span><strong>{item.odds[side]}</strong></button>)}</div></article>;
      })}</div>
    </section><aside className="game-slip sports-slip"><h2>Tu boleto <span className="virtual-badge">SIMPLE</span></h2><p className="stake-summary">Saldo <strong>{wallet ? `${wallet.balance} CRD` : '…'}</strong></p>
      {selected ? <><div className="slip-selection"><small>FÚTBOL SIMULADO · RESULTADO FINAL</small><h3>{labels[selected.side]}</h3><p>{selected.event.home} vs {selected.event.away}</p><strong>Cuota {selected.odds}</strong></div><label htmlFor="sports-amount">Importe en créditos</label><input id="sports-amount" type="number" min="10" max="500" step="0.01" value={amount} disabled={busy || Boolean(pending)} onChange={e => setAmount(e.target.value)} /><p className="stake-summary">Pago total si aciertas <strong>{Number.isFinite(potential) ? potential.toFixed(2) : '0.00'} CRD</strong></p><small>Incluye la devolución de tu apuesta. No es una ganancia garantizada.</small><button className="primary-action" disabled={busy || (!marketOpen && !pending) || !amount || Number(amount) < 10 || Number(amount) > 500} onClick={submit}>{busy ? 'Registrando…' : pending ? 'Reintentar mismo boleto' : 'Confirmar apuesta'}</button>{!marketOpen && !pending && <p>Este mercado ya cerró. Elige otro encuentro.</p>}</> : <div className="empty-slip"><span aria-hidden="true">◎</span><p>Selecciona una cuota para preparar tu boleto.</p></div>}
      {error && <p role="alert" className="game-error">{error}</p>}{message && <p role="status" className="game-success">{message}</p>}<Link to="/juego-responsable">Consultar mi límite diario →</Link><p className="muted">Apuestas de 10 a 500 CRD. No se aceptan apuestas después del inicio. Los marcadores y pagos los genera el simulador del servidor.</p>
    </aside></div>
    <section className="tickets-panel"><div className="section-heading"><h2>Mis boletos deportivos</h2><Link to="/historial">Historial completo →</Link></div>{tickets.length ? <div className="ticket-grid">{tickets.map(ticket => <article key={ticket.id} className="ticket-card"><strong>{ticket.description}</strong><p>{ticket.amount} CRD · cuota {ticket.odds}</p><span>{ticket.payout == null ? 'Pendiente de resultado simulado' : `Pago total: ${ticket.payout} CRD`}</span></article>)}</div> : <p className="muted">Tus boletos aparecerán aquí.</p>}</section>
  </main>;
}
