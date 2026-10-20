import { useState } from 'react';
export default function LowBalanceNotice({ threshold }) {
  const [dismissed, setDismissed] = useState(false);
  if (dismissed) return null;
  return <aside role="status" className="low-balance-notice"><p>Saldo bajo: tienes menos de {threshold} CRD disponibles. Puedes tomar un descanso.</p><button onClick={() => setDismissed(true)} aria-label="Cerrar aviso de saldo bajo">Cerrar aviso</button></aside>;
}
