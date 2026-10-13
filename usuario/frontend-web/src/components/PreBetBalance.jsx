import { useState } from 'react';
import { formatCredits } from '../utils/walletUtils';

export default function PreBetBalance({ balance }) {
  const [showTooltip, setShowTooltip] = useState(false);

  return (
    <section className="prebet-balance">
      <h3>Mesa de juego</h3>
      <p>Consulta tus créditos antes de apostar.</p>

      <div className="balance-display">
        <span>Saldo disponible:</span>
        <strong>{formatCredits(balance)}</strong>

        <button
          type="button"
          className="balance-info-button"
          aria-label="Información del saldo"
          aria-expanded={showTooltip}
          onClick={() => setShowTooltip(!showTooltip)}
        >
          ⓘ
        </button>

        {showTooltip && (
          <div className="balance-tooltip" role="status">
            Tienes {formatCredits(balance)} disponibles
            para realizar apuestas simuladas.
          </div>
        )}
      </div>
    </section>
  );
}
