export const credits = (value) => `${Number(value || 0).toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} CRD`;
export const dateTime = (iso) => iso ? new Date(iso).toLocaleString('es-PE', { timeZone: 'America/Lima', dateStyle: 'short', timeStyle: 'short' }) : '—';
export const date = (iso) => iso ? new Date(iso).toLocaleDateString('es-PE', { timeZone: 'America/Lima' }) : '—';
export const percent = (value) => value == null ? 'Sin dato de ayer' : `${value > 0 ? '+' : ''}${value.toLocaleString('es-PE', { maximumFractionDigits: 2 })}%`;
