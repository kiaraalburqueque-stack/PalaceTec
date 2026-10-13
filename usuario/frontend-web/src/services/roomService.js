import { apiRequest } from './api';
export const getCasinoRooms = () => apiRequest('/api/v1/rooms');
export const getRoomState = (id) => apiRequest('/api/v1/rooms/' + id + '/state');
export const placeRouletteBet = (id, data) => apiRequest('/api/v1/rooms/' + id + '/bets', { method: 'POST', body: JSON.stringify(data) });
export const getRoomBets = (id) => apiRequest('/api/v1/rooms/' + id + '/bets');
