import { apiRequest } from './api';
export const getCasinoRooms = (gameId) => apiRequest('/api/v1/rooms' + (gameId ? '?game=' + encodeURIComponent(gameId) : ''));
const query = (params = {}) => {
  const text = new URLSearchParams(Object.entries(params).filter(([, value]) => value != null && value !== '')).toString();
  return text ? '?' + text : '';
};
export const getGames = (params) => apiRequest('/api/v1/games' + query(params));
export const getGameCategories = (params) => apiRequest('/api/v1/games/categories' + query(params));
export const getRoomState = (id) => apiRequest('/api/v1/rooms/' + id + '/state');
export const placeRouletteBet = (id, data) => apiRequest('/api/v1/rooms/' + id + '/bets', { method: 'POST', body: JSON.stringify(data) });
export const getRoomBets = (id) => apiRequest('/api/v1/rooms/' + id + '/bets');
export const getPreviousBets = (id) => apiRequest(`/api/v1/rooms/${id}/bets/previous`);
export const repeatBets = (id, data) => apiRequest(`/api/v1/rooms/${id}/bets/repeat`, { method: 'POST', body: JSON.stringify(data) });
export const getGameRules = (id) => apiRequest(`/api/v1/games/${id}/rules`);
// HU63: favoritos del jugador.
export const getFavoriteGames = (params) => apiRequest('/api/v1/games/favorites' + query(params));
export const toggleFavorite = (id) => apiRequest(`/api/v1/games/${id}/favorite`, { method: 'POST', body: '{}' });
// HU70: recomendaciones de la sección Para ti.
export const getRecommendations = () => apiRequest('/api/v1/games/recommendations');
// HU64: juegos destacados del carrusel.
export const getFeaturedGames = () => apiRequest('/api/v1/games/featured');
