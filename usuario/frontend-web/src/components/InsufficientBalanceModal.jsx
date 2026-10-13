
export default function InsufficientBalanceModal({
  isOpen,
  onClose,
  onRecharge,
}) {
  if (!isOpen) return null;

  return (
    <div className="modal-overlay">
      <div
        className="insufficient-balance-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="insufficient-balance-title"
      >
        <div className="modal-warning-icon">⚠️</div>

        <h2 id="insufficient-balance-title">
          Saldo Insuficiente
        </h2>

        <p>
          No tienes suficientes créditos disponibles
          para realizar esta apuesta.
        </p>

        <div className="modal-actions">
          <button
            type="button"
            className="recharge-button"
            onClick={onRecharge}
          >
            Recargar créditos
          </button>

          <button
            type="button"
            className="close-modal-button"
            onClick={onClose}
          >
            Cerrar
          </button>
        </div>
      </div>
    </div>
  );
}
