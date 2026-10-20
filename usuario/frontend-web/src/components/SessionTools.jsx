import useUser from '../hooks/useUser';
import RealityCheck from './RealityCheck';
import RiskAlerts from './RiskAlerts';
export default function SessionTools() {
  const { user } = useUser();
  return user ? <div key={user.id}><RealityCheck /><RiskAlerts userId={user.id} /></div> : null;
}
