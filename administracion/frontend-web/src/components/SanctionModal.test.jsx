import { it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import SanctionModal from './SanctionModal';
import { setUserStatus } from '../services/adminService';
vi.mock('../services/adminService', () => ({ getSanctions: vi.fn().mockResolvedValue([]), setUserStatus: vi.fn() }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });
const player = { id: 7, email: 'jugador@palacetec.pe', status: 'ACTIVE' };
it('HU42 exige el motivo antes de suspender', async () => {
  const user = userEvent.setup();
  render(<SanctionModal user={player} onClose={() => {}} onDone={() => {}} />);
  await user.click(screen.getByRole('button', { name: 'Confirmar suspensión' }));
  expect(screen.getByRole('alert')).toHaveTextContent('motivo');
  expect(setUserStatus).not.toHaveBeenCalled();
});
it('HU42 suspende con el motivo indicado', async () => {
  setUserStatus.mockResolvedValue({});
  const onDone = vi.fn(); const user = userEvent.setup();
  render(<SanctionModal user={player} onClose={() => {}} onDone={onDone} />);
  await user.type(screen.getByLabelText('Motivo de la sanción *'), 'Uso de varias cuentas');
  await user.click(screen.getByRole('button', { name: 'Confirmar suspensión' }));
  expect(setUserStatus).toHaveBeenCalledWith(7, false, 'Uso de varias cuentas');
  expect(onDone).toHaveBeenCalled();
});
