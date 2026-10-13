import { useEffect, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import useUser from '../hooks/useUser';
import { getWalletSummary } from '../services/walletService';
import { getRoomBets, getRoomState } from '../services/roomService';
import { connectRoom } from '../services/roomSocket';
import PreBetBalance from '../components/PreBetBalance';
import BetControls from '../components/BetControls';

export default function RoulettePage() {
  const { roomId } = useParams();
  const { user, loading: sessionLoading } = useUser();
  const [wallet, setWallet] = useState(null);
  const [state, setState] = useState(null);
  const [history, setHistory] = useState([]);
  const [status, setStatus] = useState('Conectando');
  const [error, setError] = useState('');
  const [now, setNow] = useState(() => Date.now());
  const [offset, setOffset] = useState(0);
  const [result, setResult] = useState(null);
  const [prize, setPrize] = useState(null);
  useEffect(() => {
    if (!roomId || !user) return;
    let active = true;
    async function sync() {
      try {
        const [snapshot, summary, bets] = await Promise.all([getRoomState(roomId), getWalletSummary(), getRoomBets(roomId)]);
        if (!active) return;
        setState(snapshot); setWallet(summary); setHistory(bets); setError('');
        setOffset(Date.parse(snapshot.server_time) - Date.now());
        if (snapshot.round?.winning_number != null) setResult(snapshot.round.winning_number);
      } catch (err) { if (active) setError(err.message); }
    }
    void sync();
    const stop = connectRoom(roomId, ({ event, data }) => {
      if (!active) return;
      if (event === 'room:state') { setState(data); setOffset(Date.parse(data.server_time) - Date.now()); }
      if (event === 'timer:start') setState((old) => ({ ...old, round: data }));
      if (event === 'timer:tick') setState((old) => old?.round?.id === data.round_id ? { ...old, round: { ...old.round, status: 'OPEN', closes_at: data.closes_at } } : old);
      if (event === 'bets:closed') setState((old) => old?.round?.id === data.round_id ? { ...old, round: { ...old.round, status: 'CLOSED' } } : old);
      if (event === 'wallet:update') setWallet(data);
      if (event === 'round:result') { setResult(data.winning_number); void sync(); }
      if (event === 'round:winnings') { setPrize(data); void sync(); }
    }, (value) => { if (active) setStatus(value); });
    const tick = setInterval(() => setNow(Date.now()), 250);
    const fallback = setInterval(sync, 5000);
    return () => { active = false; stop(); clearInterval(tick); clearInterval(fallback); };
  }, [roomId, user]);
  const refresh = async () => {
    const [summary, bets] = await Promise.all([getWalletSummary(), getRoomBets(roomId)]);
    setWallet(summary); setHistory(bets);
  };
  if (sessionLoading) return <p role="status">Consultando sesión...</p>;
  if (!user) return <Navigate to="/login" replace />;
  if (!roomId) return <Navigate to="/" replace />;
  const round = state?.round;
  const seconds = round?.status === 'OPEN' ? Math.max(0, Math.ceil((Date.parse(round.closes_at) - now - offset) / 1000)) : 0;
  const open = round?.status === 'OPEN' && seconds > 0;
  return <main className="main-sections roulette-page">
    <h1>Ruleta europea · Sala {roomId}</h1>
    <p>Conexión: {status}</p><Link to="/">Volver a salas</Link>
    {error && <p role="alert">{error}</p>}
    <section className="prebet-balance round-panel"><h2>Ronda {round?.id || 'pendiente'}</h2>
      <p role="timer" aria-label="Tiempo para apostar">{seconds} segundos</p>
      <strong>{open ? 'Apuestas abiertas' : 'NO VA MÁS · Apuestas cerradas'}</strong>
      {state && <p>Mínimo por apuesta: {state.min_bet} CRD · Máximo acumulado: {state.max_bet} CRD</p>}
      {result !== null && <h3>Último número ganador: {result}</h3>}
    </section>
    {wallet ? <><PreBetBalance balance={Number(wallet.balance)} /><p>Bono: {wallet.bonus_balance} CRD · Ganancias: {wallet.winnings_balance} CRD</p>
      <BetControls key={roomId} balance={Number(wallet.balance)} roomId={roomId} onBetPlaced={refresh} roundOpen={open} currentRound={round ? { ...round, remaining_seconds: seconds } : null} winningNumber={result} />
    </> : <p role="status">Consultando saldo...</p>}
    {prize && <section role={Number(prize.payout) > 0 ? 'dialog' : 'status'} aria-label="Resultado de tu ronda" className="prebet-balance">
      <h2>{Number(prize.payout) > 0 ? '¡Ganaste!' : 'Ronda terminada'}</h2><p>Ronda {prize.round_id}: pago total {prize.payout} CRD</p>
      <button onClick={() => setPrize(null)}>Cerrar resultado</button>
    </section>}
    <section className="prebet-balance history-panel"><h2>Mis apuestas</h2>
      {history.length === 0 ? <p>Todavía no hay apuestas en esta sala.</p> : <table><thead><tr><th>Ronda</th><th>Tipo</th><th>Selección</th><th>Apuesta</th><th>Pago</th></tr></thead><tbody>{history.map((bet) => <tr key={bet.id}><td>{bet.round_id}</td><td>{bet.kind}</td><td>{bet.selection}</td><td>{bet.amount}</td><td>{bet.payout ?? 'Pendiente'}</td></tr>)}</tbody></table>}
    </section>
  </main>;
}
