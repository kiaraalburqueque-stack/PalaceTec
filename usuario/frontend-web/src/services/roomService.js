import { apiRequest } from './api';
export const getCasinoRooms = (gameId) => apiRequest('/api/v1/rooms' + (gameId ? '?game=' + encodeURIComponent(gameId) : ''));
export const getGames = () => apiRequest('/api/v1/games');
export const getRoomState = (id) => apiRequest('/api/v1/rooms/' + id + '/state');
export const placeRouletteBet = (id, data) => apiRequest('/api/v1/rooms/' + id + '/bets', { method: 'POST', body: JSON.stringify(data) });
export const getRoomBets = (id) => apiRequest('/api/v1/rooms/' + id + '/bets');
export const getPreviousBets = (id) => apiRequest(`/api/v1/rooms/${id}/bets/previous`);
export const repeatBets = (id, data) => apiRequest(`/api/v1/rooms/${id}/bets/repeat`, { method: 'POST', body: JSON.stringify(data) });
