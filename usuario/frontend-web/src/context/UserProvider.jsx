import { useCallback, useEffect, useRef, useState } from 'react';
import { UserContext } from './UserContext';
import { getUserProfile } from '../services/authService';
export default function UserProvider({ children }) {
  const [user, setUserState] = useState(null);
  const [loading, setLoading] = useState(true);
  const changed = useRef(false);
  const setUser = useCallback((value) => { changed.current = true; setUserState(value); setLoading(false); }, []);
  useEffect(() => {
    let active = true;
    getUserProfile().then((profile) => {
      if (active && !changed.current) setUserState({ ...profile, name: profile.nickname || profile.email.split('@')[0], nickname: profile.nickname || profile.email.split('@')[0] });
    }).catch(() => { /* Sin cookie válida se muestra visitante. */ }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);
  return <UserContext.Provider value={{ user, setUser, loading }}>{children}</UserContext.Provider>;
}
