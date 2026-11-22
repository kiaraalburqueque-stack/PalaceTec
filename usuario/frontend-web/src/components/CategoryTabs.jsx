// HU61: pestañas del catálogo con ícono y contador de juegos por categoría.
const ICONS = { '': '★', ruleta: '◉', cartas: '♠', deportes: '⚽', otros: '✦', favoritos: '♥' };

export default function CategoryTabs({ tabs, active, onChange }) {
  return <div className="category-tabs" role="tablist" aria-label="Categorías de juegos">
    {tabs.map((tab) => <button key={tab.slug || 'todos'} type="button" role="tab" aria-selected={active === tab.slug}
      className={active === tab.slug ? 'is-active' : ''} onClick={() => onChange(tab.slug)}>
      <span aria-hidden="true">{ICONS[tab.slug] || '✦'}</span> {tab.label}
      {tab.count != null && <span className="tab-count" aria-label={`${tab.count} juegos`}>{tab.count}</span>}
    </button>)}
  </div>;
}
