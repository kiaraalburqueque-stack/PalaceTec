import { it, expect, vi, afterEach, beforeAll } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import ExportButtons from './ExportButtons';
import { downloadHistory } from '../services/historyService';
vi.mock('../services/historyService', async (original) => ({ ...(await original()), downloadHistory: vi.fn() }));
afterEach(() => { cleanup(); vi.clearAllMocks(); });
beforeAll(() => { URL.createObjectURL = vi.fn(() => 'blob:archivo'); URL.revokeObjectURL = vi.fn(); });

it('HU66 exporta en PDF con el filtro de fechas y muestra el estado de carga', async () => {
  const user = userEvent.setup();
  let finish;
  downloadHistory.mockReturnValue(new Promise((resolve) => { finish = resolve; }));
  const clicked = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
  render(<ExportButtons filters={{ startDate: '2026-11-01', endDate: '2026-11-10' }} />);
  await user.click(screen.getByRole('button', { name: 'Exportar PDF' }));
  expect(downloadHistory).toHaveBeenCalledWith('pdf', { startDate: '2026-11-01', endDate: '2026-11-10' });
  expect(screen.getByRole('button', { name: 'Generando PDF…' })).toBeDisabled();
  expect(screen.getByRole('button', { name: 'Exportar Excel' })).toBeDisabled();
  finish(new Blob(['%PDF']));
  expect(await screen.findByRole('button', { name: 'Exportar PDF' })).toBeEnabled();
  expect(clicked).toHaveBeenCalled();
  expect(clicked.mock.contexts[0].download).toBe('historial_palacetec_2026-11-01_2026-11-10.pdf');
  clicked.mockRestore();
});

it('HU66 exporta en Excel sin fechas y avisa si falla', async () => {
  const user = userEvent.setup();
  downloadHistory.mockRejectedValue(new Error('La fecha final debe ser igual o posterior al inicio.'));
  render(<ExportButtons filters={{}} />);
  await user.click(screen.getByRole('button', { name: 'Exportar Excel' }));
  expect(downloadHistory).toHaveBeenCalledWith('xlsx', {});
  expect(await screen.findByRole('alert')).toHaveTextContent('fecha final');
});
