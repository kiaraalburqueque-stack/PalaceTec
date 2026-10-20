
import { useState } from 'react';
import InsufficientBalanceModal from './InsufficientBalanceModal';
import RouletteBoard from './RouletteBoard';
import { formatCredits } from '../utils/walletUtils';
import { checkPreBet } from '../services/walletService';
import { getRoomState, placeRouletteBet } from '../services/roomService';

function playChipSound() {
  const AudioContextClass =
    window.AudioContext || window.webkitAudioContext;

  if (!AudioContextClass) return;

  try {
    const audioContext = new AudioContextClass();
    const oscillator = audioContext.createOscillator();
    const gain = audioContext.createGain();

    oscillator.type = 'sine';
    oscillator.frequency.setValueAtTime(650, audioContext.currentTime);

    gain.gain.setValueAtTime(0.08, audioContext.currentTime);
    gain.gain.exponentialRampToValueAtTime(
      0.001,
      audioContext.currentTime + 0.08
    );

    oscillator.connect(gain);
    gain.connect(audioContext.destination);

    oscillator.start();
    oscillator.stop(audioContext.currentTime + 0.08);

    oscillator.onended = () => {
      audioContext.close();
    };
  } catch {
    // Si el navegador no permite audio, la selección sigue funcionando.
  }
}

export default function BetControls({
  balance,
  roomId,
  onBetPlaced,
  roundOpen = true,
  currentRound,
  winningNumber,
}) {
  const [pendingBet, setPendingBet] = useState(null);
  const [betAmount, setBetAmount] = useState(10);
  const [draft, setDraft] = useState({ roundId: null, chips: [] });
  const roundId = currentRound?.id ?? null;
  const chips = draft.roundId === roundId && roundOpen ? draft.chips : [];
  function setChips(value) { setDraft({ roundId, chips: typeof value === 'function' ? value(chips) : value }); }
  const total = chips.length ? chips.reduce((sum, chip) => sum + chip, 0) : betAmount;
  const [betKind, setBetKind] = useState('COLOR');
  const [selection, setSelection] = useState('rojo');
  const [number, setNumber] = useState('0');

  const [showModal, setShowModal] = useState(false);
  const [isShaking, setIsShaking] = useState(false);
  const [isChecking, setIsChecking] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const handleNumberSelect = (selectedNumber) => {
  const value = String(selectedNumber);

  setBetKind('STRAIGHT_UP');
  setNumber(value);
  setSelection(value);
  setError('');
  setMessage('');
};

  const handleKindChange = (kind) => {
    setBetKind(kind);
    setError('');
    setMessage('');

    if (kind === 'COLOR') {
      setSelection('rojo');
    } else if (kind === 'PARITY') {
      setSelection('par');
    } else if (kind === 'DOZEN' || kind === 'COLUMN') {
      setSelection('1');
    } else {
      setSelection(number);
    }
  };

  const handleBet = async () => {
    if (isChecking || (!roundOpen && !pendingBet)) return;

    setError('');
    setMessage('');

    if (total > balance && !pendingBet) {
      setShowModal(true);
      setIsShaking(true);
      return;
    }

    setIsChecking(true);

    try {
      const check = pendingBet ? { can_bet: true } : await checkPreBet(total);

      if (!check.can_bet) {
        setShowModal(true);
        setIsShaking(true);
        return;
      }

      // Mantiene el comportamiento de las pruebas existentes
      // cuando se usa el componente sin una sala.
      if (!roomId) {
        setMessage(
          'Saldo validado correctamente. La apuesta aún no ha sido registrada.'
        );
        return;
      }

      // Consulta la ronda justo antes de apostar.
      const state = currentRound ? { round: currentRound } : await getRoomState(roomId);
      const round = state.round;

      if (
        !pendingBet && (!round ||
        round.status !== 'OPEN' ||
        round.remaining_seconds <= 0)
      ) {
        setError(
          'La ronda está cerrada. Espera a que comience la siguiente.'
        );
        return;
      }

      const betData = pendingBet || {
        round_id: round.id,
        kind: betKind,
        selection:
          betKind === 'STRAIGHT_UP' ? number : selection,
        chips: chips.length ? [...chips] : [betAmount],
        reference: crypto.randomUUID(),
      };

      setPendingBet(betData);

      await placeRouletteBet(roomId, betData);

      setPendingBet(null);
      setChips([]);
      setMessage('¡Apuesta registrada correctamente!');

      if (onBetPlaced) {
        await onBetPlaced();
      }
    } catch (err) {
      if (err.status && err.status < 500) setPendingBet(null);
      if (err?.code === 'insufficient_balance') {
        setShowModal(true);
        setIsShaking(true);
      } else if (err?.code === 'bets_closed') {
        setError(
          'La ronda cerró antes de registrar tu apuesta. Intenta en la siguiente.'
        );
      } else {
        setError(
          err instanceof Error
            ? err.message
            : 'No se pudo registrar la apuesta.'
        );
      }
    } finally {
      setIsChecking(false);
    }
  };

  const handleRecharge = () => {
    setShowModal(false);
    // Pendiente: conectar el flujo de recarga.
  };

  return (
    <section className="bet-controls">
      <fieldset disabled={isChecking || !!pendingBet} style={{ border: 0, padding: 0, minWidth: 0 }}>
      <h3>Realizar apuesta</h3>

      <p>Saldo disponible: {formatCredits(balance)}</p>

      <label htmlFor="bet-kind">Tipo de apuesta:</label>

      <select
        id="bet-kind"
        value={betKind}
        onChange={(event) =>
          handleKindChange(event.target.value)
        }
      >
        <option value="COLOR">Color</option>
        <option value="PARITY">Paridad</option>
        <option value="STRAIGHT_UP">Número exacto</option>
        <option value="DOZEN">Docena</option><option value="COLUMN">Columna</option>
      </select>

      {betKind === 'COLOR' && (
        <div>
          <p>Selecciona un color:</p>

          <select
            aria-label="Selecciona un color"
            value={selection}
            onChange={(event) =>
              setSelection(event.target.value)
            }
          >
            <option value="rojo">Rojo</option>
            <option value="negro">Negro</option>
          </select>
        </div>
      )}

      {betKind === 'PARITY' && (
        <div>
          <p>Selecciona una paridad:</p>

          <select
            aria-label="Selecciona una paridad"
            value={selection}
            onChange={(event) =>
              setSelection(event.target.value)
            }
          >
            <option value="par">Par</option>
            <option value="impar">Impar</option>
          </select>
        </div>
      )}

      {(betKind === 'DOZEN' || betKind === 'COLUMN') && <label>Selecciona {betKind === 'DOZEN' ? 'docena' : 'columna'}<select aria-label="Docena o columna" value={selection} onChange={(e) => setSelection(e.target.value)}>{['1', '2', '3'].map((value) => <option key={value} value={value}>{betKind === 'DOZEN' ? `${(Number(value)-1)*12+1}–${Number(value)*12}` : `${value}, ${Number(value)+3}, …`}</option>)}</select></label>}
      {betKind === 'STRAIGHT_UP' && (
        <div>
          <label htmlFor="bet-number">
            Selecciona un número:
          </label>

          <select
            id="bet-number"
            value={number}
            onChange={(event) =>
              setNumber(event.target.value)
            }
          >
            {Array.from({ length: 37 }, (_, index) => (
              <option key={index} value={String(index)}>
                {index}
              </option>
            ))}
          </select>
        </div>
      )}

      <RouletteBoard
        selectedNumber={betKind === 'STRAIGHT_UP' ? Number(number) : null}
        winningNumber={winningNumber}
        onSelectNumber={handleNumberSelect}
        disabled={isChecking || !roundOpen}
      />
      
      <p>Selecciona una ficha:</p>

      <div
        className={`bet-chips ${isShaking ? 'shake' : ''}`}
        onAnimationEnd={() => setIsShaking(false)}
      >
        {[10, 50, 100, 500].map((amount) => (
          <button
            key={amount}
            type="button"
            className={`bet-chip ${
              betAmount === amount ? 'selected' : ''
            }`}
            onClick={() => {
              setBetAmount(amount);
              if (amount > balance) { setShowModal(true); setIsShaking(true); }
              setError('');
              setMessage('');
              playChipSound();
            }}
            aria-pressed={betAmount === amount}
          >
            {amount}
          </button>
        ))}
      </div>

      <p>
        Ficha seleccionada: {formatCredits(betAmount)}
      </p>

      <button type="button" disabled={!roundOpen || chips.length >= 100 || (chips.length ? total : 0) + betAmount > balance} onClick={() => setChips((old) => [...old, betAmount])}>Agregar ficha</button>
      <button type="button" onClick={() => setChips([])} disabled={!chips.length}>Limpiar fichas</button>
      <p>Total de esta apuesta: {formatCredits(total)} · {chips.length || 1} fichas</p>
      <small>Docenas y columnas: pago total ×3. El cero pierde.</small>
      </fieldset>
      <button
        type="button"
        className="place-bet-button"
        onClick={handleBet}
        disabled={isChecking || (!pendingBet && (!roundOpen || total > balance))}
      >
        {isChecking ? 'Procesando...' : pendingBet ? 'Reintentar apuesta pendiente' : 'Apostar'}
      </button>

      {total > balance && <p role="alert">Saldo insuficiente para esta ficha.</p>}
      {error && <p role="alert">{error}</p>}

      {message && <p role="status">{message}</p>}

      <InsufficientBalanceModal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        onRecharge={handleRecharge}
      />
    </section>
  );
}
