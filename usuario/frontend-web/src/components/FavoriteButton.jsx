import { useState } from 'react';
import { toggleFavorite } from '../services/roomService';

// HU63: corazón de la tarjeta; marca o desmarca el juego y guarda el cambio en el servidor.
export default function FavoriteButton({ game, onChange }) {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const active = Boolean(game.is_favorite);
  async function toggle() {
    setSaving(true); setError('');
    try {
      const result = await toggleFavorite(game.id);
      onChange?.(game.id, result.is_favorite);
    } catch (err) {
      setError(err.message || 'No se pudo actualizar el favorito.');
    } finally { setSaving(false); }
  }
  return <>
    <button type="button" className={`favorite-button ${active ? 'is-active' : ''}`} aria-pressed={active} disabled={saving}
      aria-label={active ? `Quitar ${game.title} de favoritos` : `Marcar ${game.title} como favorito`} onClick={toggle}>
      <span aria-hidden="true">{active ? '♥' : '♡'}</span>
    </button>
    {error && <small role="alert" className="favorite-error">{error}</small>}
  </>;
}
