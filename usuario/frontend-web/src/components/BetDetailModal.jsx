import { useEffect, useState } from 'react';
import DialogModal from './DialogModal';
import { getBetDetail } from '../services/historyService';

// HU54: recrea el tapete con las fichas que el jugador colocó en una apuesta pasada.
const RED = new Set([1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36]);
const KINDS = { STRAIGHT_UP: 'Pleno', COLOR: 'Color', PARITY: 'Paridad', DOZEN: 'Docena', COLUMN: 'Columna' };
const STATUS = { PENDING: 'Pendiente', WON: 'Ganada', LOST: 'Perdida' };
const CELL = 40; const GAP = 3; const X0 = 4 + CELL + GAP; const Y0 = 4;

function numberCell(number) {
  if (number === 0) return { x: 4, y: Y0, w: CELL, h: CELL * 3 + GAP * 2 };
  const column = Math.floor((number - 1) / 3); const row = 2 - ((number - 1) % 3);
  return { x: X0 + column * (CELL + GAP), y: Y0 + row * (CELL + GAP), w: CELL, h: CELL };
}

const ROW1 = Y0 + 3 * (CELL + GAP); const ROW2 = ROW1 + 34 + GAP; const SIX = (CELL + GAP) * 2 - GAP;
const OUTSIDE = {
  'DOZEN:1': { x: X0, y: ROW1, w: (CELL + GAP) * 4 - GAP, h: 34, label: '1ª docena' },
  'DOZEN:2': { x: X0 + (CELL + GAP) * 4, y: ROW1, w: (CELL + GAP) * 4 - GAP, h: 34, label: '2ª docena' },
  'DOZEN:3': { x: X0 + (CELL + GAP) * 8, y: ROW1, w: (CELL + GAP) * 4 - GAP, h: 34, label: '3ª docena' },
  'PARITY:par': { x: X0 + SIX + GAP, y: ROW2, w: SIX, h: 34, label: 'Par' },
  'COLOR:rojo': { x: X0 + (SIX + GAP) * 2, y: ROW2, w: SIX, h: 34, label: 'Rojo', fill: '#bb2737' },
  'COLOR:negro': { x: X0 + (SIX + GAP) * 3, y: ROW2, w: SIX, h: 34, label: 'Negro', fill: '#202633' },
  'PARITY:impar': { x: X0 + (SIX + GAP) * 4, y: ROW2, w: SIX, h: 34, label: 'Impar' },
  'COLUMN:3': { x: X0 + 12 * (CELL + GAP), y: Y0, w: CELL + 8, h: CELL, label: '2:1' },
  'COLUMN:2': { x: X0 + 12 * (CELL + GAP), y: Y0 + CELL + GAP, w: CELL + 8, h: CELL, label: '2:1' },
  'COLUMN:1': { x: X0 + 12 * (CELL + GAP), y: Y0 + 2 * (CELL + GAP), w: CELL + 8, h: CELL, label: '2:1' },
};

export function betTarget(bet) {
  if (bet.kind === 'STRAIGHT_UP') return numberCell(Number(bet.selection));
  return OUTSIDE[`${bet.kind}:${bet.selection}`];
}

export function BetTable({ bet }) {
  const target = betTarget(bet);
  const winning = bet.round?.winning_number;
  const width = X0 + 12 * (CELL + GAP) + CELL + 12;
  const height = ROW2 + 34 + 4;
  const cx = target ? target.x + target.w / 2 : 0; const cy = target ? target.y + target.h / 2 : 0;
  return <svg viewBox={`0 0 ${width} ${height}`} className="bet-detail-table" role="img"
    aria-label={`Tapete con ${bet.chips.length} fichas en ${KINDS[bet.kind]} ${bet.selection}`}>
    {Array.from({ length: 37 }, (_, number) => { const cell = numberCell(number);
      const fill = number === 0 ? '#16834a' : RED.has(number) ? '#bb2737' : '#202633';
      return <g key={number}><rect x={cell.x} y={cell.y} width={cell.w} height={cell.h} rx="4" fill={fill}
        stroke={winning === number ? '#ffd76a' : '#e8dcc0'} strokeWidth={winning === number ? 3 : 1} />
        <text x={cell.x + cell.w / 2} y={cell.y + cell.h / 2 + 5} textAnchor="middle" className="table-number">{number}</text></g>; })}
    {Object.entries(OUTSIDE).map(([key, cell]) => <g key={key}><rect x={cell.x} y={cell.y} width={cell.w} height={cell.h} rx="4"
      fill={cell.fill || '#1d5a40'} stroke="#e8dcc0" strokeWidth="1" />
      <text x={cell.x + cell.w / 2} y={cell.y + cell.h / 2 + 5} textAnchor="middle" className="table-number small">{cell.label}</text></g>)}
    {target && bet.chips.slice(0, 8).map((chip, index) => <g key={index} className="table-chip" transform={`translate(${cx} ${cy - index * 4})`}>
      <circle r="13" /><circle r="9" className="chip-inner" />
      {index === Math.min(bet.chips.length, 8) - 1 && <text y="4" textAnchor="middle">{bet.chips.length > 1 ? `×${bet.chips.length}` : chip}</text>}
    </g>)}
  </svg>;
}

export default function BetDetailModal({ betId, onClose }) {
  const [bet, setBet] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    getBetDetail(betId).then((value) => { if (active) setBet(value); }).catch((err) => { if (active) setError(err.message); });
    return () => { active = false; };
  }, [betId]);
  return <DialogModal title={`Apuesta #${betId}`} onClose={onClose} className="bet-detail-modal">
    {error && <p role="alert">{error}</p>}
    {!bet && !error && <p role="status">Cargando detalle...</p>}
    {bet && <>
      <BetTable bet={bet} />
      <dl className="bet-detail-grid">
        <div><dt>Juego</dt><dd>{bet.game} · {bet.room_name}</dd></div>
        <div><dt>Ronda</dt><dd>{bet.round.id}</dd></div>
        <div><dt>Apuesta</dt><dd>{KINDS[bet.kind]}: {bet.selection}</dd></div>
        <div><dt>Fichas</dt><dd>{bet.chips.join(' + ')} = {bet.amount} CRD</dd></div>
        <div><dt>Número ganador</dt><dd>{bet.round.winning_number ?? 'Pendiente'}{bet.round.winning_color ? ` (${bet.round.winning_color})` : ''}</dd></div>
        <div><dt>Estado</dt><dd className={`status-${bet.status.toLowerCase()}`}>{STATUS[bet.status]}</dd></div>
        <div><dt>Pago total</dt><dd>{bet.payout == null ? 'Pendiente' : `${bet.payout} CRD`}</dd></div>
        <div><dt>Neto</dt><dd>{bet.net == null ? 'Pendiente' : `${bet.net} CRD`}</dd></div>
      </dl>
      <small>Registrada el {new Date(bet.created_at).toLocaleString('es-PE', { timeZone: 'America/Lima' })} (Lima).</small>
    </>}
  </DialogModal>;
}
