import { it, expect, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import '@testing-library/jest-dom/vitest';
import AdminGate from './AdminGate';
import { UserContext } from '../context/UserContext';
afterEach(cleanup);
it('HU39 bloquea rutas administrativas de usuarios comunes', () => {
  render(<MemoryRouter><UserContext.Provider value={{ user: { id: 1 }, role: 'USER' }}><AdminGate><p>Panel privado</p></AdminGate></UserContext.Provider></MemoryRouter>);
  expect(screen.getByRole('alert')).toHaveTextContent('permisos de administrador');
  expect(screen.queryByText('Panel privado')).not.toBeInTheDocument();
});
it('HU39 permite al administrador entrar al panel', () => {
  render(<MemoryRouter><UserContext.Provider value={{ user: { id: 1 }, role: 'ADMIN' }}><AdminGate><p>Panel privado</p></AdminGate></UserContext.Provider></MemoryRouter>);
  expect(screen.getByText('Panel privado')).toBeInTheDocument();
});
