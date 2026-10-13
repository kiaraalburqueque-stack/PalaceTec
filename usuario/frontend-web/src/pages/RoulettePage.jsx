
import PreBetBalance from '../components/PreBetBalance';
import BetControls from '../components/BetControls';

export default function RoulettePage() {
  const demoBalance = 1000;

  return (
    <main className="main-sections">
      <h1>Mesa de Ruleta</h1>

      <PreBetBalance balance={demoBalance} />
      <BetControls balance={demoBalance} />
    </main>
  );
}
