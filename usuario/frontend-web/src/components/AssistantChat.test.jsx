import { it, expect, vi, afterEach, beforeEach } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom/vitest';
import AssistantChat from './AssistantChat';
import { getAssistantHistory, sendAssistantMessage } from '../services/assistantService';

const session = { user: { id: 1 } };
vi.mock('../hooks/useUser', () => ({ default: () => session }));
vi.mock('../services/assistantService', () => ({ getAssistantHistory: vi.fn(), sendAssistantMessage: vi.fn() }));
afterEach(cleanup);
beforeEach(() => {
  session.user = { id: 1 };
  getAssistantHistory.mockResolvedValue({ messages: [], reminder: 'Juega con responsabilidad.' });
});

it('HU68 no se muestra sin sesión', () => {
  session.user = null;
  render(<AssistantChat />);
  expect(screen.queryByRole('button', { name: 'Abrir asistente virtual' })).not.toBeInTheDocument();
});

it('HU68 envía la pregunta, muestra Escribiendo y luego la respuesta', async () => {
  const user = userEvent.setup();
  let resolve;
  sendAssistantMessage.mockReturnValue(new Promise((done) => { resolve = done; }));
  render(<AssistantChat />);
  await user.click(screen.getByRole('button', { name: 'Abrir asistente virtual' }));
  expect(await screen.findByRole('dialog', { name: 'Asistente virtual de PalaceTec' })).toBeInTheDocument();
  expect(await screen.findByText(/Pregúntame por las reglas/)).toBeInTheDocument();
  expect(screen.getByText('Juega con responsabilidad.')).toBeInTheDocument();
  await user.type(screen.getByRole('textbox', { name: 'Escribe tu pregunta' }), '¿Cómo se juega la ruleta?');
  await user.click(screen.getByRole('button', { name: 'Enviar' }));
  expect(sendAssistantMessage).toHaveBeenCalledWith('¿Cómo se juega la ruleta?');
  expect(screen.getByRole('status')).toHaveTextContent('Escribiendo');
  expect(screen.getByRole('log')).toHaveTextContent('¿Cómo se juega la ruleta?');
  resolve({ question: { id: 1, role: 'USER', content: '¿Cómo se juega la ruleta?' },
    reply: { id: 2, role: 'ASSISTANT', content: 'Ruleta europea: 37 casillas.', source: 'faq', data: {} }, reminder: 'Juega con responsabilidad.' });
  expect(await screen.findByText('Ruleta europea: 37 casillas.')).toBeInTheDocument();
  expect(screen.queryByText('Escribiendo…')).not.toBeInTheDocument();
  expect(screen.getByRole('textbox', { name: 'Escribe tu pregunta' })).toHaveValue('');
});

it('HU68 carga el historial y avisa cuando se supera el límite de mensajes', async () => {
  const user = userEvent.setup();
  getAssistantHistory.mockResolvedValue({ messages: [{ id: 5, role: 'ASSISTANT', content: 'Respuesta anterior', data: {} }], reminder: 'R' });
  sendAssistantMessage.mockRejectedValue(Object.assign(new Error('Too many'), { status: 429 }));
  render(<AssistantChat />);
  await user.click(screen.getByRole('button', { name: 'Abrir asistente virtual' }));
  expect(await screen.findByText('Respuesta anterior')).toBeInTheDocument();
  await user.type(screen.getByRole('textbox', { name: 'Escribe tu pregunta' }), 'hola');
  await user.click(screen.getByRole('button', { name: 'Enviar' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Espera un momento');
  expect(screen.getByRole('textbox', { name: 'Escribe tu pregunta' })).toHaveValue('hola');
});

it('HU68 las sugerencias envían la pregunta con un clic', async () => {
  const user = userEvent.setup();
  sendAssistantMessage.mockResolvedValue({ question: { id: 1, role: 'USER', content: '¿Cuál es mi límite diario?' },
    reply: { id: 2, role: 'ASSISTANT', content: 'Tu límite diario es de 100.00 créditos.', data: {} } });
  render(<AssistantChat />);
  await user.click(screen.getByRole('button', { name: 'Abrir asistente virtual' }));
  await user.click(await screen.findByRole('button', { name: '¿Cuál es mi límite diario?' }));
  expect(await screen.findByText('Tu límite diario es de 100.00 créditos.')).toBeInTheDocument();
});
