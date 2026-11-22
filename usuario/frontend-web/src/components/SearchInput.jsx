// HU62: campo de búsqueda con botón para limpiar.
export default function SearchInput({ value, onChange, label = 'Buscar juegos', placeholder = 'Busca por nombre…' }) {
  return <div className="search-input" role="search">
    <span aria-hidden="true">⌕</span>
    <input type="search" aria-label={label} placeholder={placeholder} value={value} maxLength={80}
      onChange={(event) => onChange(event.target.value)} />
    {value && <button type="button" onClick={() => onChange('')} aria-label="Limpiar búsqueda">×</button>}
  </div>;
}
