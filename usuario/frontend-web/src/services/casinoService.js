import { apiRequest } from './api';
const post = (path, data) => apiRequest('/api/v1/casino/' + path, { method: 'POST', body: JSON.stringify(data) });
export const getBlackjack = () => apiRequest('/api/v1/casino/blackjack');
export const startBlackjack = (data) => post('blackjack', data);
export const actBlackjack = (id, data) => post(`blackjack/${id}/action`, data);
export const getSportEvents = () => apiRequest('/api/v1/casino/sports/events');
export const getSportTickets = () => apiRequest('/api/v1/casino/sports/tickets');
export const placeSportTicket = (data) => post('sports/tickets', data);
