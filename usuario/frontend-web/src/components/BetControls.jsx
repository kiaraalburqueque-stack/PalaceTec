
import { useState } from 'react';
import InsufficientBalanceModal from './InsufficientBalanceModal';
import { formatCredits } from '../utils/walletUtils';

export default function BetControls({ balance }) {
  const [betAmount, setBetAmount] = useState(10);
  const [showModal, setShowModal] = useState(false);
  const [isShaking, setIsShaking] = useState(false);

  const handleBet = () => {
    if (betAmount > balance) {
      setShowModal(true);
      setIsShaking(true);
      return;
    }

    // La apuesta real se conectará con el backend.
  };

  const handleRecharge = () => {
    setShowModal(false);
    // Pendiente: conectar con el flujo de recarga.
  };

  return (
    <section className="bet-controls">
      <h3>Realizar apuesta</h3>

      <p>Saldo disponible: {formatCredits(balance)}</p>

      <label htmlFor="bet-amount">Selecciona una ficha:</label>

      <div className={`bet-chips ${isShaking ? 'shake' : ''}`}
        onAnimationEnd={() => setIsShaking(false)}
      >
        {[10, 50, 100, 500].map((amount) => (
          <button
            key={amount}
            type="button"
            className={`bet-chip ${betAmount === amount ? 'selected' : ''}`}
            onClick={() => setBetAmount(amount)}
          >
            {amount}
          </button>
        ))}
      </div>

      <p>Ficha seleccionada: {formatCredits(betAmount)}</p>

      <button
        type="button"
        className="place-bet-button"
        onClick={handleBet}
      >
        Apostar
      </button>

      <InsufficientBalanceModal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        onRecharge={handleRecharge}
      />
    </section>
  );
}
