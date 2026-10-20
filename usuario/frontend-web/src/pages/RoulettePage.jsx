import { useEffect, useRef, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import useUser from '../hooks/useUser';
import { getWalletSummary } from '../services/walletService';
import { getRoomBets, getRoomState } from '../services/roomService';
import { connectRoom } from '../services/roomSocket';
import PreBetBalance from '../components/PreBetBalance';
import BetControls from '../components/BetControls';
import RouletteWheel from '../components/RouletteWheel';
import { secondsLeft } from '../utils/roundTime';
import RoundResult from '../components/RoundResult';
import RecentResults from '../components/RecentResults';
import RepeatBets from '../components/RepeatBets';
import { getDailyLimit, getRiskAlerts } from '../services/responsibleService';
import { snapshotResult } from '../utils/snapshotResult';

export default function RoulettePage() {
  const { roomId } = useParams();
  const { user, loading } = useUser();
  if (loading) return <p role="status">Consultando sesión...</p>;
  if (!user) return <Navigate to="/login" replace />;
  if (!roomId) return <Navigate to="/" replace />;
  return <RoomSession key={`${user.id}:${roomId}`} roomId={roomId} user={user} />;
}

function RoomSession({ roomId, user }) {
  const [wallet, setWallet] = useState(null);
  const [dailyLimit, setDailyLimit] = useState(null);
  const [pauseUntil, setPauseUntil] = useState(null);
  const [state, setState] = useState(null);
  const [history, setHistory] = useState([]);
  const [status, setStatus] = useState('Conectando');
  const [error, setError] = useState('');
  const [now, setNow] = useState(() => Date.now());
  const [offset, setOffset] = useState(0);
  const [result, setResult] = useState(null);
  const [prize, setPrize] = useState(null);
  const [outcome, setOutcome] = useState(null);
  const [confirmedResult, setConfirmedResult] = useState(null);
  const seenResults = useRef(new Set());
  useEffect(() => {
    if (!roomId || !user) return;
    let active = true;
    async function sync() {
      try {
        const [snapshot, summary, bets, limit, risk] = await Promise.all([getRoomState(roomId), getWalletSummary(), getRoomBets(roomId), getDailyLimit(), getRiskAlerts()]);
        if (!active) return;
        setState(snapshot); setWallet(summary); setHistory(bets); setError('');
        setDailyLimit(limit);
        setPauseUntil(risk.pause_until);
        window.dispatchEvent(new CustomEvent('palacetec:wallet-update', { detail: { userId: user.id, wallet: summary } }));
        setOffset(Date.parse(snapshot.server_time) - Date.now());
        const last = snapshotResult(snapshot);
        if (last) { setConfirmedResult(last); setResult(last.winning_number); setOutcome({ roundId: last.round_id, number: last.winning_number }); }
      } catch (err) { if (active) setError(err.message); }
    }
    void sync();
    const stop = connectRoom(roomId, ({ event, data }) => {
      if (!active) return;
      if (event === 'responsible:risk-alert') window.dispatchEvent(new CustomEvent('palacetec:risk-alert', { detail: { userId: user.id, ...data } }));
      if (event === 'room:state') { setState(data); setOffset(Date.parse(data.server_time) - Date.now()); }
      if (event === 'timer:start') setState((old) => ({ ...old, round: data }));
      if (event === 'timer:tick') setState((old) => old?.round?.id === data.round_id ? { ...old, round: { ...old.round, status: 'OPEN', closes_at: data.closes_at } } : old);
      if (event === 'bets:closed' || event === 'timer:stop') setState((old) => old?.round?.id === data.round_id ? { ...old, round: { ...old.round, status: 'CLOSED' } } : old);
      if (event === 'wallet:update') {
        setWallet(data);
        window.dispatchEvent(new CustomEvent('palacetec:wallet-update', { detail: { userId: user.id, wallet: data } }));
      }
      if (event === 'round:result' && !seenResults.current.has(data.round_id)) { seenResults.current.add(data.round_id); setConfirmedResult(data); setResult(data.winning_number); setOutcome({ roundId: data.round_id, number: data.winning_number }); void sync(); }
      if (event === 'round:winnings') { setPrize(data); void sync(); }
    }, (value) => { if (active) setStatus(value); });
    const tick = setInterval(() => setNow(Date.now()), 250);
    const fallback = setInterval(sync, 5000);
    const pause = (event) => { if (event.detail?.userId === user.id) setPauseUntil(event.detail.pause_until); };
    window.addEventListener('palacetec:pause', pause);
    return () => { active = false; stop(); clearInterval(tick); clearInterval(fallback); window.removeEventListener('palacetec:pause', pause); };
  }, [roomId, user]);
  const refresh = async () => {
    const [summary, bets, limit] = await Promise.all([getWalletSummary(), getRoomBets(roomId), getDailyLimit()]);
    setWallet(summary); setHistory(bets); setDailyLimit(limit);
    window.dispatchEvent(new CustomEvent('palacetec:wallet-update', { detail: { userId: user.id, wallet: summary } }));
  };
  const round = state?.round;
  const seconds = secondsLeft(round, now, offset);
  const roundOpen = status === 'En vivo' && seconds > 0;
  const paused = Date.parse(pauseUntil) > now;
  const open = roundOpen && dailyLimit?.configured && Number(dailyLimit.remaining) >= Number(state?.min_bet || 10) && !paused;
  return <main className="main-sections roulette-page">
    <h1>Ruleta europea · Sala {roomId}</h1>
    <p>Conexión: {status}</p><Link to="/">Volver a salas</Link>
    {error && <p role="alert">{error}</p>}
    <p><Link to="/juego-responsable">Mis límites y juego responsable</Link> · {dailyLimit?.configured ? `Cupo diario disponible: ${dailyLimit.remaining} CRD` : 'Configura tu límite diario para apostar.'}</p>
    <section className="prebet-balance round-panel"><h2>Ronda {round?.id || 'pendiente'}</h2>
      <p role="timer" aria-label="Tiempo para apostar">{seconds} segundos</p>
      <strong>{roundOpen ? 'Apuestas abiertas' : 'NO VA MÁS · Apuestas cerradas'}</strong>
      {paused && <p role="status">Tu pausa está activa. No puedes apostar hasta {new Date(pauseUntil).toLocaleTimeString('es-PE', { timeZone: 'America/Lima' })} (Lima).</p>}
      {dailyLimit?.configured && Number(dailyLimit.remaining) < Number(state?.min_bet || 10) && <p role="status">Tu cupo diario no alcanza para la apuesta mínima.</p>}
      {state && <p>Mínimo por apuesta: {state.min_bet} CRD · Máximo acumulado: {state.max_bet} CRD</p>}
      {result !== null && <h3>Último número ganador: {result}</h3>}
    </section>
    <RouletteWheel key={`wheel-${roomId}`} spinning={round?.status === 'CLOSED' || (round?.status === 'OPEN' && seconds === 0)} outcome={outcome} />
    <RoundResult result={confirmedResult} />
    <RecentResults results={state?.recent_results} />
    <RepeatBets key={`repeat-${roomId}`} roomId={roomId} roundId={round?.id} open={open} balance={Number(wallet?.balance || 0)} remaining={dailyLimit?.remaining || '0.00'} onRepeated={refresh} />
    {wallet ? <><div className="wallet-panel"><PreBetBalance balance={Number(wallet.balance)} /><p>Bono: {wallet.bonus_balance} CRD · Ganancias: {wallet.winnings_balance} CRD</p></div>
      <BetControls key={`bets-${roomId}`} balance={Number(wallet.balance)} roomId={roomId} onBetPlaced={refresh} roundOpen={open} currentRound={round ? { ...round, remaining_seconds: seconds } : null} winningNumber={result} />
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
