import { apiRequest } from './api';
export const getCasinoRooms = (gameId) => apiRequest('/api/v1/rooms' + (gameId ? '?game=' + encodeURIComponent(gameId) : ''));
export const getGames = () => apiRequest('/api/v1/games');
export const getRoomState = (id) => apiRequest('/api/v1/rooms/' + id + '/state');
export const placeRouletteBet = (id, data) => apiRequest('/api/v1/rooms/' + id + '/bets', { method: 'POST', body: JSON.stringify(data) });
export const getRoomBets = (id) => apiRequest('/api/v1/rooms/' + id + '/bets');
