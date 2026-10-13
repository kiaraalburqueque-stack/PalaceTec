import { useState } from 'react';
import { UserContext } from './UserContext';

const initialUser = {
  id: 1,
  name: 'Jugador de Prueba',
  nickname: 'JugadorDemo',
  email: 'jugador@palacetec.test',
};

export default function UserProvider({ children }) {
  const [user, setUser] = useState(initialUser);

  return (
    <UserContext.Provider value={{ user, setUser }}>
      {children}
    </UserContext.Provider>
  );
}
