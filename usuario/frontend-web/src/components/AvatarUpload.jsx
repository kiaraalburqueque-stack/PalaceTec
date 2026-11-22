import { useEffect, useRef, useState } from 'react';
import { uploadAvatar } from '../services/authService';
import { avatarFileError } from '../utils/avatar';

// HU65: selector de foto con vista previa; el servidor la recorta a 300 x 300 px.
export default function AvatarUpload({ onUploaded }) {
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [saving, setSaving] = useState(false);
  const previewUrl = useRef('');
  useEffect(() => () => { if (previewUrl.current) URL.revokeObjectURL(previewUrl.current); }, []);
  function showPreview(selected) {
    if (previewUrl.current) URL.revokeObjectURL(previewUrl.current);
    previewUrl.current = selected ? URL.createObjectURL(selected) : '';
    setPreview(previewUrl.current);
  }
  function choose(event) {
    const selected = event.target.files?.[0] || null;
    setMessage('');
    const problem = selected ? avatarFileError(selected) : '';
    setError(problem);
    setFile(problem ? null : selected);
    showPreview(problem ? null : selected);
    if (problem) event.target.value = '';
  }
  async function send() {
    setSaving(true); setError('');
    try {
      const profile = await uploadAvatar(file);
      setFile(null); showPreview(null); setMessage('Foto actualizada.');
      onUploaded(profile);
    } catch (err) { setError(err.message); }
    finally { setSaving(false); }
  }
  return <section className="bet-controls auth-form avatar-upload" aria-label="Foto de avatar">
    <label>Subir mi foto (JPG, PNG o WEBP, hasta 2 MB)
      <input type="file" accept="image/jpeg,image/png,image/webp" onChange={choose} disabled={saving} />
    </label>
    {preview && <img className="avatar-preview" src={preview} alt="Vista previa de la foto" width="96" height="96" />}
    {file && <button type="button" className="place-bet-button" onClick={send} disabled={saving}>{saving ? 'Subiendo...' : 'Usar esta foto'}</button>}
    {error && <small role="alert">{error}</small>}
    {message && <small className="avatar-upload-ok">{message}</small>}
  </section>;
}
