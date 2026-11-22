import { it, expect, vi, afterEach, beforeAll } from 'vitest';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import BusinessReport from './BusinessReport';
import { downloadBusinessReport } from '../services/adminService';
vi.mock('../services/adminService', () => ({ downloadBusinessReport: vi.fn() }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });
beforeAll(() => { URL.createObjectURL = vi.fn(() => 'blob:x'); URL.revokeObjectURL = vi.fn(); });

it('HU73 descarga el informe del rango elegido con nombre por fechas', async () => {
  const user = userEvent.setup();
  let finish;
  downloadBusinessReport.mockReturnValue(new Promise((resolve) => { finish = resolve; }));
  const clicked = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  render(<BusinessReport />);
  fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2026-11-01' } });
  fireEvent.change(screen.getByLabelText('Hasta'), { target: { value: '2026-11-15' } });
  await user.click(screen.getByRole('button', { name: 'Descargar informe' }));
  expect(downloadBusinessReport).toHaveBeenCalledWith({ startDate: '2026-11-01', endDate: '2026-11-15' });
  expect(screen.getByRole('button', { name: 'Generando informe…' })).toBeDisabled();
  finish(new Blob(['xlsx']));
  expect(await screen.findByRole('button', { name: 'Descargar informe' })).toBeEnabled();
  expect(clicked.mock.contexts[0].download).toBe('informe_negocio_2026-11-01_2026-11-15.xlsx');
  clicked.mockRestore();
});

it('HU73 valida el rango antes de descargar', async () => {
  const user = userEvent.setup();
  render(<BusinessReport />);
  fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2026-11-20' } });
  fireEvent.change(screen.getByLabelText('Hasta'), { target: { value: '2026-11-01' } });
  await user.click(screen.getByRole('button', { name: 'Descargar informe' }));
  expect(screen.getByRole('alert')).toHaveTextContent('posterior');
  expect(downloadBusinessReport).not.toHaveBeenCalled();
});
