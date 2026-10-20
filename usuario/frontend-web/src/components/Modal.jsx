import { useEffect, useRef } from 'react';
export default function Modal({ title, children }) {
  const ref = useRef(null);
  useEffect(() => {
    const dialog = ref.current;
    if (dialog.showModal) dialog.showModal();
    else dialog.setAttribute('open', '');
    return () => { dialog.close?.(); };
  }, []);
  return <dialog ref={ref} className="session-modal" aria-label={title} aria-modal="true" onCancel={(event) => event.preventDefault()}>
    <h2>{title}</h2>{children}
  </dialog>;
}
