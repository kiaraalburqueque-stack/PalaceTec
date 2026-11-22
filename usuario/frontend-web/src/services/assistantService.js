import { apiRequest } from './api';
// HU68: asistente virtual.
export const getAssistantHistory = () => apiRequest('/api/v1/assistant/chat');
export const sendAssistantMessage = (message) => apiRequest('/api/v1/assistant/chat', { method: 'POST', body: JSON.stringify({ message }) });
