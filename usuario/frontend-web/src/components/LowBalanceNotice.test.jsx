import { it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import LowBalanceNotice from './LowBalanceNotice';
it('HU36 no repite avisos cerrados durante el mismo episodio de saldo bajo', async () => {
  const user = userEvent.setup(); const view = render(<LowBalanceNotice threshold="10.00" />);
  expect(screen.getByRole('status')).toHaveTextContent('10.00 CRD');
  await user.click(screen.getByRole('button'));
  view.rerender(<LowBalanceNotice threshold="10.00" />);
  expect(screen.queryByRole('status')).not.toBeInTheDocument();
});
