import { useCallback, useEffect, useState } from 'react';
import { getWalletSummary } from '../services/walletService';

export default function useGameWallet(user) {
  const [state, setState] = useState(null);
  const refresh = useCallback(async () => {
    if (!user) return;
    try {
      const wallet = await getWalletSummary();
      setState({ userId: user.id, wallet });
      window.dispatchEvent(new CustomEvent('palacetec:wallet-update', { detail: { userId: user.id, wallet } }));
    } catch { /* El header y el siguiente sondeo recuperan el saldo. */ }
  }, [user]);
  useEffect(() => {
    const initial = setTimeout(refresh, 0);
    const timer = setInterval(refresh, 5000);
    return () => { clearTimeout(initial); clearInterval(timer); };
  }, [refresh]);
  return { wallet: state?.userId === user?.id ? state?.wallet : null, refresh };
}
