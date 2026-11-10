export default function Loading({ label = 'Cargando...' }) {
  return <div className="loading" role="status"><span className="spinner" aria-hidden="true" />{label}</div>;
}
