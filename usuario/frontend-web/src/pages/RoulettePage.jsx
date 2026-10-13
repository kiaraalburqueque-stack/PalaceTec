
import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';

import PreBetBalance from '../components/PreBetBalance';
import BetControls from '../components/BetControls';
import { getWalletBalance } from '../services/walletService';
import { getRoomState } from '../services/roomService';

export default function RoulettePage() {
  const { roomId } = useParams();

  const [balance, setBalance] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [roomState, setRoomState] = useState(null);
  const [roomError, setRoomError] = useState('');

  useEffect(() => {
    let active = true;

    async function loadBalance() {
      setLoading(true);
      setError('');
      setBalance(null);

      try {
        const realBalance = await getWalletBalance();

        if (active) {
          setBalance(realBalance);
        }
      } catch (err) {
        if (active) {
          setError(
            err instanceof Error
              ? err.message
              : 'No se pudo consultar el saldo del jugador.'
          );
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadBalance();

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!roomId) {
      return;
    }

    let active = true;

    async function loadRoomState() {
      try {
        const state = await getRoomState(roomId);

        if (active) {
          setRoomState(state);
          setRoomError('');
        }
      } catch (err) {
        if (active) {
          setRoomError(
            err instanceof Error
              ? err.message
              : 'No se pudo consultar la ronda.'
          );
        }
      }
    }

    loadRoomState();

    const interval = setInterval(loadRoomState, 3000);

    return () => {
      active = false;
      clearInterval(interval);
    };
  }, [roomId]);

  const refreshBalance = async () => {
    const updatedBalance = await getWalletBalance();
    setBalance(updatedBalance);
  };

  const currentRound = roomState?.round;

  const isRoundOpen =
    currentRound?.status === 'OPEN' &&
    currentRound.remaining_seconds > 0;

  return (
    <main className="main-sections">
      <h1>Mesa de Ruleta</h1>

      {roomId && <p>Estás en la sala número {roomId}</p>}

      {roomId && (
        <section className="prebet-balance">
          <h3>Estado de la ronda</h3>

          {roomError && <p role="alert">{roomError}</p>}

          {!roomState && !roomError && (
            <p role="status">Consultando la ronda...</p>
          )}

          {roomState && !currentRound && (
            <p>Todavía no hay una ronda disponible.</p>
          )}

          {currentRound && (
            <>
              <p>Ronda: #{currentRound.id}</p>

              <p>
                Estado:{' '}
                <strong>
                  {isRoundOpen
                    ? 'Apuestas abiertas'
                    : 'Apuestas cerradas'}
                </strong>
              </p>

              <p>
                Tiempo restante:{' '}
                {currentRound.remaining_seconds} segundos
              </p>

              <p>Apuesta mínima: {roomState.min_bet} CRD</p>

              <p>Apuesta máxima: {roomState.max_bet} CRD</p>
            </>
          )}
        </section>
      )}

      {loading && <p role="status">Consultando tu saldo...</p>}

      {error && <p role="alert">{error}</p>}

      {!loading && !error && balance !== null && (
        <>
          <PreBetBalance balance={balance} />

          <BetControls
            balance={balance}
            roomId={roomId}
            onBetPlaced={refreshBalance}
          />
        </>
      )}
    </main>
  );
}
