import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import DialogModal from './DialogModal';
import { getGameRules } from '../services/roomService';

// HU60: reglas, tabla de pagos y RTP con acceso directo a la sala.
export default function GameRulesModal({ gameId, onClose }) {
  const navigate = useNavigate();
  const [rules, setRules] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    getGameRules(gameId).then((data) => { if (active) setRules(data); }).catch((err) => { if (active) setError(err.message); });
    return () => { active = false; };
  }, [gameId]);
  return <DialogModal title={rules ? `Reglas · ${rules.title}` : 'Reglas del juego'} onClose={onClose} className="rules-modal">
    {error && <p role="alert">{error}</p>}
    {!rules && !error && <p role="status">Cargando reglas...</p>}
    {rules && <>
      <div className="rules-stats">
        <div><span>RTP</span><strong>{rules.rtp}%</strong></div>
        <div><span>Ventaja de la casa</span><strong>{rules.house_edge}%</strong></div>
        <div><span>Apuestas</span><strong>{rules.min_bet} – {rules.max_bet} CRD</strong></div>
      </div>
      {rules.rules && <p className="rules-text">{rules.rules}</p>}
      {rules.payout_table.length > 0 && <table className="payout-table"><caption>Tabla de pagos</caption>
        <thead><tr><th>Apuesta</th><th>Cubre</th><th>Paga</th><th>Probabilidad</th></tr></thead>
        <tbody>{rules.payout_table.map((row) => <tr key={row.bet}><td>{row.bet}</td><td>{row.description}</td><td><strong>{row.pays}</strong></td><td>{row.probability}</td></tr>)}</tbody>
      </table>}
      <small>RTP (retorno teórico al jugador) es el porcentaje de lo apostado que el juego devuelve a largo plazo. Créditos virtuales, sin dinero real.</small>
      <div className="rules-actions">
        <button type="button" className="close-modal-button" onClick={onClose}>Cerrar</button>
        <button type="button" className="place-bet-button" disabled={!rules.play_room_id}
          onClick={() => { onClose(); navigate(`/ruleta/${rules.play_room_id}`); }}>Jugar ahora</button>
      </div>
    </>}
  </DialogModal>;
}
