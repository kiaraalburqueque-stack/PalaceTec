import { useCallback, useEffect, useState } from 'react';
import { AdminContext } from './AdminContext';
import { getMe } from '../services/adminService';
import { refreshSession } from '../services/api';

// Solo deja pasar cuentas con rol ADMIN (HU39); un USER ve "acceso restringido".
export default function AdminProvider({ children }) {
  const [admin, setAdmin] = useState(null);
  const [loading, setLoading] = useState(true);
  const reload = useCallback(async () => {
    try { setAdmin(await getMe()); } catch { setAdmin(null); } finally { setLoading(false); }
  }, []);
  useEffect(() => { void reload(); }, [reload]);
  useEffect(() => {
    const ended = () => setAdmin(null);
    window.addEventListener('palacetec:session-ended', ended);
    return () => window.removeEventListener('palacetec:session-ended', ended);
  }, []);
  useEffect(() => {
    if (!admin) return undefined;
    const timer = setInterval(() => { void refreshSession().catch(() => {}); }, 10 * 60 * 1000);
    return () => clearInterval(timer);
  }, [admin]);
  return <AdminContext.Provider value={{ admin, setAdmin, loading, reload }}>{children}</AdminContext.Provider>;
}
