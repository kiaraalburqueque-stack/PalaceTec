import { it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import SelfExclusion from './SelfExclusion';
import { selfExclude } from '../services/responsibleService';
import { endSession } from '../services/api';
vi.mock('../services/responsibleService', () => ({ selfExclude: vi.fn() }));
vi.mock('../services/api', () => ({ endSession: vi.fn() }));
it('HU27 exige confirmación y termina la sesión tras excluirse', async () => {
  selfExclude.mockResolvedValue({ self_excluded_until: '2026-10-26T12:00:00Z' });
  const user = userEvent.setup(); render(<SelfExclusion />);
  expect(screen.getByRole('button')).toBeDisabled();
  await user.selectOptions(screen.getByLabelText('Duración'), '30');
  await user.click(screen.getByRole('checkbox'));
  await user.click(screen.getByRole('button'));
  expect(selfExclude).toHaveBeenCalledWith('30');
  expect(endSession).toHaveBeenCalled();
});
