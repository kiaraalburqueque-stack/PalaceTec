import { useId } from 'react';

// Íconos de la interfaz en SVG. Son decorativos: el texto del control lleva el nombre accesible.
const fill = (d) => <path fill="currentColor" d={d} />;
const stroke = { fill: 'none', stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round', strokeLinejoin: 'round' };

const ICONS = {
  home: fill('M12 3.2 2.8 11h2.7v9.3h5.2v-6h2.6v6h5.2V11h2.7L12 3.2Z'),
  roulette: <g {...stroke}>
    <circle cx="12" cy="12" r="9" /><circle cx="12" cy="12" r="5.2" /><circle cx="12" cy="12" r="1.6" fill="currentColor" />
    <path d="M12 3v3.8M12 17.2V21M3 12h3.8M17.2 12H21M5.6 5.6l2.7 2.7M15.7 15.7l2.7 2.7M18.4 5.6l-2.7 2.7M8.3 15.7l-2.7 2.7" />
  </g>,
  cards: <g {...stroke}>
    <rect x="3.5" y="6" width="11" height="15" rx="1.8" /><path d="M9 3.6 18.8 5.4a1.8 1.8 0 0 1 1.5 2.1L18.4 18" />
    <path d="M9 10.2c-1.4 1.2-2.4 2.2-2.4 3.2a1.3 1.3 0 0 0 2.4.7 1.3 1.3 0 0 0 2.4-.7c0-1-1-2-2.4-3.2Z" fill="currentColor" stroke="none" />
  </g>,
  ball: <g {...stroke}>
    <circle cx="12" cy="12" r="9" /><path d="m12 8.2 3.4 2.4-1.3 4h-4.2l-1.3-4L12 8.2Z" fill="currentColor" />
    <path d="M12 8.2V3.4M15.4 10.6l4.3-1.6M14.1 14.6l2.6 3.8M9.9 14.6l-2.6 3.8M8.6 10.6 4.3 9" />
  </g>,
  history: fill('M13 3a9 9 0 0 0-9 9H1l3.9 3.9L8.8 12H6a7 7 0 1 1 2.1 5l-1.4 1.4A9 9 0 1 0 13 3Zm-1 5v5l4.3 2.5.8-1.3-3.6-2.1V8H12Z'),
  stats: fill('M4 20h16v1.6H2.4V3H4v17Zm2-2v-7h3v7H6Zm5 0V6h3v12h-3Zm5 0v-5h3v5h-3Z'),
  shield: fill('M12 2 4 5v6.1c0 5 3.4 9.7 8 10.9 4.6-1.2 8-5.9 8-10.9V5l-8-3Zm-1.2 14.3-3.6-3.6 1.4-1.4 2.2 2.2 5-5 1.4 1.4-6.4 6.4Z'),
  userPlus: fill('M15 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm0 2c-2.7 0-8 1.3-8 4v2h16v-2c0-2.7-5.3-4-8-4ZM6 10V7H4v3H1v2h3v3h2v-3h3v-2H6Z'),
  login: fill('M10 17v-3H3v-4h7V7l5 5-5 5Zm2-14h7c1.1 0 2 .9 2 2v14c0 1.1-.9 2-2 2h-7v-2h7V5h-7V3Z'),
  coin: <g {...stroke}>
    <circle cx="12" cy="12" r="9" /><circle cx="12" cy="12" r="6" strokeDasharray="2.2 2.2" />
    <path d="M12 9v6M10 10.5h3a1 1 0 0 1 0 2h-2a1 1 0 0 0 0 2h3" strokeWidth="1.6" />
  </g>,
  chevron: fill('M6.4 8.6 12 14.2l5.6-5.6L19 10l-7 7-7-7 1.4-1.4Z'),
  info: fill('M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Zm1 15h-2v-6h2v6Zm0-8h-2V7h2v2Z'),
};

export default function Icon({ name, size = 18, className = '' }) {
  const icon = ICONS[name];
  if (!icon) return null;
  return <svg className={`ui-icon ${className}`.trim()} viewBox="0 0 24 24" width={size} height={size} aria-hidden="true" focusable="false">{icon}</svg>;
}

// Marca de PalaceTec: corona dorada sobre una ficha de casino.
export function BrandMark({ size = 38 }) {
  const gold = `brand-gold-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`;
  return <svg className="brand-mark" viewBox="0 0 48 48" width={size} height={size} aria-hidden="true" focusable="false">
    <defs>
      <linearGradient id={gold} x1="0" y1="0" x2="1" y2="1">
        <stop offset="0" stopColor="#FFE082" /><stop offset=".55" stopColor="#FFC107" /><stop offset="1" stopColor="#E0A000" />
      </linearGradient>
    </defs>
    <circle cx="24" cy="24" r="22" fill="#0F1A14" stroke={`url(#${gold})`} strokeWidth="3" />
    <circle cx="24" cy="24" r="16.5" fill="none" stroke="#00C853" strokeWidth="2" strokeDasharray="4.3 4.3" />
    <path d="M14 19.5 19 23.5 24 15.5 29 23.5 34 19.5 32 31.5H16L14 19.5Z" fill={`url(#${gold})`} />
    <rect x="16" y="33" width="16" height="2.6" rx="1.3" fill={`url(#${gold})`} />
  </svg>;
}
