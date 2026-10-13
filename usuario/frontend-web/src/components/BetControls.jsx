
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
}) {
  const [betAmount, setBetAmount] = useState(10);
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
    } else {
      setSelection(number);
    }
  };

  const handleBet = async () => {
    if (isChecking) return;

    setError('');
    setMessage('');

    if (betAmount > balance) {
      setShowModal(true);
      setIsShaking(true);
      return;
    }

    setIsChecking(true);

    try {
      const check = await checkPreBet(betAmount);

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
      const state = await getRoomState(roomId);
      const round = state.round;

      if (
        !round ||
        round.status !== 'OPEN' ||
        round.remaining_seconds <= 0
      ) {
        setError(
          'La ronda está cerrada. Espera a que comience la siguiente.'
        );
        return;
      }

      const betData = {
        round_id: round.id,
        kind: betKind,
        selection:
          betKind === 'STRAIGHT_UP' ? number : selection,
        chips: [betAmount],
        reference: crypto.randomUUID(),
      };

      await placeRouletteBet(roomId, betData);

      setMessage('¡Apuesta registrada correctamente!');

      if (onBetPlaced) {
        await onBetPlaced();
      }
    } catch (err) {
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
        onSelectNumber={handleNumberSelect}
        disabled={isChecking}
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

      <button
        type="button"
        className="place-bet-button"
        onClick={handleBet}
        disabled={isChecking}
      >
        {isChecking ? 'Procesando...' : 'Apostar'}
      </button>

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
