import { useEffect, useRef, useState } from 'react';
import useUser from '../hooks/useUser';
import { getAssistantHistory, sendAssistantMessage } from '../services/assistantService';
import OddsCard from './OddsCard';

const SUGGESTIONS = ['¿Cómo se juega la ruleta?', 'Probabilidad de un pleno al 17', '¿Cuál es mi límite diario?', '¿Cuánto paga cada apuesta?'];
const DEFAULT_REMINDER = 'Juega con responsabilidad: son créditos virtuales y puedes fijar tu límite diario en Mis límites.';

// HU68: burbuja flotante de chat con historial y estado Escribiendo.
export default function AssistantChat() {
  const { user } = useUser();
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([]);
  const [loaded, setLoaded] = useState(false);
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const [reminder, setReminder] = useState(DEFAULT_REMINDER);
  const listRef = useRef(null);
  const pending = useRef(0);
  useEffect(() => {
    if (!open || loaded || !user) return;
    let active = true;
    getAssistantHistory().then((data) => {
      if (!active) return;
      setMessages(data.messages); setReminder(data.reminder || DEFAULT_REMINDER); setLoaded(true);
    }).catch((err) => { if (active) setError(err.message); });
    return () => { active = false; };
  }, [open, loaded, user]);
  useEffect(() => {
    if (listRef.current) listRef.current.scrollTop = listRef.current.scrollHeight;
  }, [messages, sending]);
  if (!user) return null;
  async function send(value) {
    const message = value.trim();
    if (!message || sending) return;
    pending.current += 1;
    const temporary = { id: `tmp-${pending.current}`, role: 'USER', content: message };
    setMessages((current) => [...current, temporary]);
    setText(''); setSending(true); setError('');
    try {
      const data = await sendAssistantMessage(message);
      setMessages((current) => [...current.filter((item) => item.id !== temporary.id), data.question, data.reply]);
      if (data.reminder) setReminder(data.reminder);
    } catch (err) {
      setMessages((current) => current.filter((item) => item.id !== temporary.id));
      setText(message);
      setError(err.status === 429 ? 'Enviaste muchos mensajes seguidos. Espera un momento.' : err.message);
    } finally { setSending(false); }
  }
  return <div className="assistant-widget">
    {open && <section className="assistant-panel" role="dialog" aria-label="Asistente virtual de PalaceTec">
      <header><div><strong>Asistente PalaceTec</strong><small>Reglas, pagos, límites y tu cuenta</small></div>
        <button type="button" className="assistant-close" onClick={() => setOpen(false)} aria-label="Cerrar asistente">×</button></header>
      <div className="assistant-messages" role="log" aria-live="polite" ref={listRef}>
        {loaded && messages.length === 0 && <p className="assistant-empty">¡Hola! Pregúntame por las reglas, los pagos o tus límites.</p>}
        {messages.map((item) => <div key={item.id} className={`assistant-message ${item.role === 'USER' ? 'from-user' : 'from-assistant'}`}>
          <span className="sr-only">{item.role === 'USER' ? 'Tú: ' : 'Asistente: '}</span>{item.content}
          {item.data?.odds && <OddsCard odds={item.data.odds} />}
        </div>)}
        {sending && <p className="assistant-typing" role="status">Escribiendo…</p>}
      </div>
      {error && <p className="assistant-error" role="alert">{error}</p>}
      {loaded && messages.length === 0 && <div className="assistant-suggestions">{SUGGESTIONS.map((item) =>
        <button key={item} type="button" onClick={() => send(item)}>{item}</button>)}</div>}
      <form onSubmit={(event) => { event.preventDefault(); void send(text); }}>
        <input aria-label="Escribe tu pregunta" placeholder="Escribe tu pregunta…" value={text} maxLength={500}
          onChange={(event) => setText(event.target.value)} />
        <button type="submit" disabled={sending || !text.trim()}>Enviar</button>
      </form>
      <small className="assistant-reminder">{reminder}</small>
    </section>}
    <button type="button" className="assistant-bubble" aria-expanded={open} onClick={() => setOpen((value) => !value)}
      aria-label={open ? 'Ocultar asistente virtual' : 'Abrir asistente virtual'}><span aria-hidden="true">{open ? '×' : '💬'}</span></button>
  </div>;
}
