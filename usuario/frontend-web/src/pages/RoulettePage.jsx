
import { useParams } from 'react-router-dom';

import PreBetBalance from '../components/PreBetBalance';
import BetControls from '../components/BetControls';

export default function RoulettePage() {
  const { roomId } = useParams();

  // Saldo temporal: se reemplazará por la billetera real.
  const demoBalance = 1000;

  return (
    <main className="main-sections">
      <h1>Mesa de Ruleta</h1>

      {roomId && (
        <p>
          Estás en la sala número {roomId}
        </p>
      )}

      <PreBetBalance balance={demoBalance} />
      <BetControls balance={demoBalance} />
    </main>
  );
}
