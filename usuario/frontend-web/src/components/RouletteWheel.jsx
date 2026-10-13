import { useEffect, useRef, useState } from 'react';

import { WHEEL_NUMBERS, winningRotation } from '../utils/rouletteWheel';
const RED = new Set([1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36]);
const STEP = 360 / 37;
const point = (radius, degrees) => [200 + radius * Math.cos(degrees * Math.PI / 180), 200 + radius * Math.sin(degrees * Math.PI / 180)];
export default function RouletteWheel({ spinning, outcome }) {
  const rotor = useRef(null);
  const animation = useRef(null);
  const rotation = useRef(0);
  const lastRound = useRef(null);
  const revealTimer = useRef(null);
  const [shown, setShown] = useState(null);
  useEffect(() => {
    if (!spinning || !rotor.current?.animate || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return;
    animation.current?.cancel();
    animation.current = rotor.current.animate([{ transform: 'rotate(' + rotation.current + 'deg)' }, { transform: 'rotate(' + (rotation.current + 360) + 'deg)' }], { duration: 800, iterations: Infinity, easing: 'linear' });
  }, [spinning]);
  useEffect(() => {
    if (outcome?.roundId == null || lastRound.current === outcome.roundId || !WHEEL_NUMBERS.includes(outcome.number)) return;
    lastRound.current = outcome.roundId;
    const element = rotor.current;
    const matrix = getComputedStyle(element).transform.match(/^matrix\(([^)]+)\)/)?.[1].split(',').map(Number);
    const visible = matrix ? Math.atan2(matrix[1], matrix[0]) * 180 / Math.PI : 0;
    const target = winningRotation(outcome.number, visible);
    animation.current?.cancel();
    rotation.current = target;
    const reveal = () => { element.style.transform = 'rotate(' + target + 'deg)'; setShown(outcome); };
    if (!element.animate || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
      revealTimer.current = setTimeout(reveal, 0); return;
    }
    animation.current = element.animate([{ transform: 'rotate(' + visible + 'deg)' }, { transform: 'rotate(' + target + 'deg)' }], { duration: 3200, easing: 'cubic-bezier(.12,.68,.12,1)', fill: 'forwards' });
    animation.current.onfinish = reveal;
  }, [outcome]);
  useEffect(() => () => { animation.current?.cancel(); clearTimeout(revealTimer.current); lastRound.current = null; }, []);
  return <section className="wheel-panel" aria-label="Ruleta circular en vivo">
    <div className="wheel-copy"><span className="eyebrow">EUROPEAN ROULETTE</span><h2>La suerte está en la rueda</h2><p>{spinning ? 'No va más. La rueda está girando…' : 'Elige tus fichas en el tapete. La rueda gira al cerrar las apuestas.'}</p><div className="wheel-result" role="status">{shown ? <>Último resultado <strong>{shown.number}</strong></> : 'Esperando el próximo resultado'}</div></div>
    <div className="wheel-frame"><div className="wheel-pointer" aria-hidden="true" />
      <svg viewBox="0 0 400 400" role="img" aria-label="Rueda europea de 37 casillas" data-winning-number={shown?.number ?? ''}>
        <circle cx="200" cy="200" r="195" fill="#b48b52" /><circle cx="200" cy="200" r="186" fill="#322b24" />
        <g ref={rotor} className="wheel-rotor">
          {WHEEL_NUMBERS.map((number, index) => {
            const center = -90 + index * STEP;
            const [x1,y1] = point(179, center - STEP / 2); const [x2,y2] = point(179, center + STEP / 2); const [tx,ty] = point(157, center);
            return <g key={number}><path d={'M200 200 L' + x1 + ' ' + y1 + ' A179 179 0 0 1 ' + x2 + ' ' + y2 + ' Z'} fill={number === 0 ? '#15915b' : RED.has(number) ? '#b83242' : '#20262d'} stroke="#c5a46a" strokeWidth=".8" /><text x={tx} y={ty} textAnchor="middle" dominantBaseline="central" fill="white" fontSize="13" fontWeight="700" transform={'rotate(' + (center + 90) + ',' + tx + ',' + ty + ')'}>{number}</text></g>;
          })}
        </g>
        <circle cx="200" cy="200" r="125" fill="#1a3629" stroke="#cfaa6b" strokeWidth="5" /><circle cx="200" cy="200" r="106" fill="#12251e" stroke="#526445" />
        <text x="200" y="193" textAnchor="middle" fill="#e2be82" fontSize="52" fontWeight="800">{spinning ? '♛' : shown?.number ?? '♛'}</text>
        <text x="200" y="231" textAnchor="middle" fill="#b9ac8d" fontSize="10" letterSpacing="2">PALACETEC</text>
      </svg>
    </div>
  </section>;
}

