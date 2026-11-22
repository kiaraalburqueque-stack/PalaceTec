export default function Pagination({ page, count, pageSize = 10, onChange, disabled }) {
  const pages = Math.max(1, Math.ceil(count / pageSize));
  return <nav className="pagination" aria-label="Paginación">
    <button type="button" disabled={disabled || page <= 1} onClick={() => onChange(page - 1)}>‹ Anterior</button>
    <span>Página {page} de {pages} · {count} registro{count === 1 ? '' : 's'}</span>
    <button type="button" disabled={disabled || page >= pages} onClick={() => onChange(page + 1)}>Siguiente ›</button>
  </nav>;
}
