import { useState } from 'react';
import { setGameFeatured } from '../services/adminService';

// HU64: interruptor Destacado de la tabla de juegos.
export default function FeaturedToggle({ game, onChanged }) {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  async function toggle() {
    setSaving(true); setError('');
    try {
      const updated = await setGameFeatured(game.id, !game.is_featured);
      onChanged(updated.is_featured ? `${game.title} ahora aparece en el carrusel de destacados.` : `${game.title} ya no está destacado.`);
    } catch (err) { setError(err.message); }
    finally { setSaving(false); }
  }
  return <span className="featured-toggle">
    <label className="switch">
      <input type="checkbox" role="switch" checked={Boolean(game.is_featured)} disabled={saving} onChange={toggle}
        aria-label={`Destacar ${game.title} en el inicio`} />
      <span aria-hidden="true" className="switch-track" />
      <span>Destacado</span>
    </label>
    {error && <small className="field-error" role="alert">{error}</small>}
  </span>;
}
