import { useEffect, useRef } from 'react';

// Ventana modal accesible: se cierra con ESC, con clic fuera del contenido o con el botón Cerrar.
export default function DialogModal({ title, onClose, children, className = '' }) {
  const ref = useRef(null);
  const closeRef = useRef(onClose);
  useEffect(() => { closeRef.current = onClose; }, [onClose]);
  useEffect(() => {
    const dialog = ref.current;
    if (dialog.showModal && !dialog.open) dialog.showModal();
    else dialog.setAttribute('open', '');
    const onKey = (event) => { if (event.key === 'Escape') { event.preventDefault(); closeRef.current?.(); } };
    document.addEventListener('keydown', onKey);
    return () => { document.removeEventListener('keydown', onKey); if (dialog.open) dialog.close?.(); };
  }, []);
  return <dialog ref={ref} className={`dialog-modal ${className}`} aria-label={title} aria-modal="true"
    onCancel={(event) => { event.preventDefault(); onClose?.(); }}
    onClick={(event) => { if (event.target === event.currentTarget) onClose?.(); }}>
    <div className="dialog-modal-content">
      <header><h2>{title}</h2><button type="button" className="dialog-close" aria-label="Cerrar" onClick={onClose}>×</button></header>
      {children}
    </div>
  </dialog>;
}
