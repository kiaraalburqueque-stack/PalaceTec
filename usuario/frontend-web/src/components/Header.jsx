
import UserProfileDropdown from './UserProfileDropdown';
import useUser from '../hooks/useUser';
import { useEffect, useState } from 'react';
import { getWalletSummary } from '../services/walletService';
import { formatCredits } from '../utils/walletUtils';
import LowBalanceNotice from './LowBalanceNotice';

export default function Header() {
  const { user, setUser } = useUser();
  const [walletState, setWalletState] = useState(null);
  const wallet = user && walletState?.userId === user.id ? walletState.wallet : null;
  useEffect(() => {
    if (!user) return;
    let active = true;
    const refresh = () => getWalletSummary().then((data) => {
      if (active) setWalletState({ userId: user.id, wallet: data });
    }).catch((error) => {
      if (!active) return;
      setWalletState(null);
      if (error.status === 401) setUser(null);
    });
    const update = (event) => {
      if (event.detail?.userId === user.id) setWalletState({ userId: user.id, wallet: event.detail.wallet });
    };
    void refresh();
    const interval = setInterval(refresh, 10000);
    window.addEventListener('focus', refresh);
    window.addEventListener('palacetec:wallet-update', update);
    return () => { active = false; clearInterval(interval); window.removeEventListener('focus', refresh); window.removeEventListener('palacetec:wallet-update', update); };
  }, [user, setUser]);

  return (
    <header className="main-header">
      <div className="header-brand">
        <span className="brand-icon">♛</span>
        <span>PalaceTec</span>
      </div>

      <nav className="header-navigation" aria-label="Navegación principal">
        <span>Casino Virtual</span>
      </nav>

      <div className="header-account">
        {wallet?.is_low_balance && <LowBalanceNotice key={user.id} threshold={wallet.low_balance_threshold} />}
        {user && <div className="header-wallet" aria-label="Saldo de billetera"><span>MI SALDO</span><strong>{wallet ? formatCredits(Number(wallet.balance)) : 'Consultando…'}</strong></div>}
        <UserProfileDropdown user={user} wallet={wallet} />
      </div>
    </header>
  );
}
