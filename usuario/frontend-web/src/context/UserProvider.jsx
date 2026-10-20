import { useCallback, useEffect, useRef, useState } from 'react';
import { UserContext } from './UserContext';
import { getUserProfile, getCurrentUser } from '../services/authService';
import { refreshSession } from '../services/api';
export default function UserProvider({ children }) {
  const [user, setUserState] = useState(null);
  const [loading, setLoading] = useState(true);
  const [roleState, setRoleState] = useState(null);
  const changed = useRef(false);
  const setUser = useCallback((value) => { changed.current = true; setUserState(value); setLoading(false); }, []);
  useEffect(() => {
    const ended = () => setUser(null);
    window.addEventListener('palacetec:session-ended', ended);
    return () => window.removeEventListener('palacetec:session-ended', ended);
  }, [setUser]);
  useEffect(() => {
    if (!user) return;
    let active = true;
    getCurrentUser().then((data) => { if (active) setRoleState({ userId: user.id, role: data.role }); })
      .catch(() => { if (active) setRoleState({ userId: user.id, role: 'DENIED' }); });
    return () => { active = false; };
  }, [user]);
  useEffect(() => {
    if (!user) return;
    const timer = setInterval(() => { void refreshSession().catch(() => {}); }, 10 * 60 * 1000);
    return () => clearInterval(timer);
  }, [user]);
  useEffect(() => {
    let active = true;
    getUserProfile().then((profile) => {
      if (active && !changed.current) setUserState({ ...profile, name: profile.nickname || profile.email.split('@')[0], nickname: profile.nickname || profile.email.split('@')[0] });
    }).catch(() => { /* Sin cookie válida se muestra visitante. */ }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);
  const role = user && roleState?.userId === user.id ? roleState.role : null;
  return <UserContext.Provider value={{ user, setUser, loading, role }}>{children}</UserContext.Provider>;
}
