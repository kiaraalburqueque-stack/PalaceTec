import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { createGame } from '../services/adminService';
import { CATEGORIES, serverErrors, slugify, validateGame } from '../utils/games';

// HU43: alta de un juego con título, slug, categoría, apuestas, RTP y portada.
export default function GameForm() {
  const navigate = useNavigate();
  const [values, setValues] = useState({ title: '', slug: '', category: 'RULETA', min_bet: '10.00', max_bet: '500.00', rtp: '97.30', is_published: true });
  const [slugTouched, setSlugTouched] = useState(false);
  const [cover, setCover] = useState(null);
  const [preview, setPreview] = useState('');
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const set = (key, value) => setValues((old) => ({ ...old, [key]: value, ...(key === 'title' && !slugTouched ? { slug: slugify(value) } : {}) }));
  function pickCover(file) {
    setErrors((old) => ({ ...old, cover: undefined }));
    if (!file) { setCover(null); setPreview(''); return; }
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) { setErrors((old) => ({ ...old, cover: 'La portada debe ser JPG, PNG o WEBP.' })); return; }
    if (file.size > 2 * 1024 * 1024) { setErrors((old) => ({ ...old, cover: 'La portada no debe superar 2 MB.' })); return; }
    setCover(file); setPreview(URL.createObjectURL ? URL.createObjectURL(file) : '');
  }
  async function submit(event) {
    event.preventDefault(); setError('');
    const found = validateGame(values);
    setErrors(found);
    if (Object.keys(found).length) return;
    const form = new FormData();
    Object.entries(values).forEach(([key, value]) => form.append(key, String(value)));
    if (cover) form.append('cover', cover);
    setSaving(true);
    try { const game = await createGame(form); navigate('/juegos', { state: { created: game.title } }); }
    catch (err) { setErrors(serverErrors(err.data)); setError(err.message); setSaving(false); }
  }
  const field = (key) => ({ id: `game-${key}`, 'aria-invalid': Boolean(errors[key]), 'aria-describedby': errors[key] ? `game-${key}-error` : undefined });
  const fieldError = (key) => errors[key] && <small className="field-error" id={`game-${key}-error`}>{errors[key]}</small>;
  return <section className="page">
    <header className="page-header"><div><h1>Nuevo juego</h1><p>Aparecerá en el catálogo del jugador con una mesa principal que usa estos límites.</p></div>
      <Link to="/juegos">← Volver al catálogo</Link></header>
    <form className="card form-grid two-columns" onSubmit={submit} noValidate>
      <div><label htmlFor="game-title">Título *</label><input {...field('title')} value={values.title} onChange={(e) => set('title', e.target.value)} maxLength={80} />{fieldError('title')}</div>
      <div><label htmlFor="game-slug">Slug *</label><input {...field('slug')} value={values.slug} onChange={(e) => { setSlugTouched(true); set('slug', e.target.value); }} maxLength={50} />{fieldError('slug')}</div>
      <div><label htmlFor="game-category">Categoría</label><select {...field('category')} value={values.category} onChange={(e) => set('category', e.target.value)}>
        {CATEGORIES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div>
      <div><label htmlFor="game-rtp">RTP (%) *</label><input {...field('rtp')} type="number" step="0.01" min="80" max="99.99" value={values.rtp} onChange={(e) => set('rtp', e.target.value)} />{fieldError('rtp')}</div>
      <div><label htmlFor="game-min_bet">Apuesta mínima (CRD) *</label><input {...field('min_bet')} type="number" step="0.01" min="1" value={values.min_bet} onChange={(e) => set('min_bet', e.target.value)} />{fieldError('min_bet')}</div>
      <div><label htmlFor="game-max_bet">Apuesta máxima (CRD) *</label><input {...field('max_bet')} type="number" step="0.01" min="1" value={values.max_bet} onChange={(e) => set('max_bet', e.target.value)} />{fieldError('max_bet')}</div>
      <div className="full"><label htmlFor="game-cover">Portada (JPG, PNG o WEBP, máx. 2 MB)</label>
        <input {...field('cover')} type="file" accept="image/jpeg,image/png,image/webp" onChange={(e) => pickCover(e.target.files[0])} />{fieldError('cover')}
        {preview && <img className="cover-preview" src={preview} alt="Vista previa de la portada" />}</div>
      <label className="checkbox full"><input type="checkbox" checked={values.is_published} onChange={(e) => set('is_published', e.target.checked)} /> Publicar de inmediato en el catálogo</label>
      {error && <p role="alert" className="full">{error}</p>}
      <div className="actions full"><Link className="secondary button-link" to="/juegos">Cancelar</Link><button className="primary" disabled={saving}>{saving ? 'Creando...' : 'Crear juego'}</button></div>
    </form>
  </section>;
}
