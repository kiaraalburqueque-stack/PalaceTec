import useUser from '../hooks/useUser';
import RealityCheck from './RealityCheck';
export default function SessionTools() {
  const { user } = useUser();
  return user ? <RealityCheck key={user.id} /> : null;
}
