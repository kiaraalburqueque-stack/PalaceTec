
const RED_NUMBERS = new Set([
  1, 3, 5, 7, 9, 12, 14, 16, 18,
  19, 21, 23, 25, 27, 30, 32, 34, 36,
]);

const NUMBERS = Array.from({ length: 37 }, (_, number) => number);

function getNumberColor(number) {
  if (number === 0) return '#16834a';
  return RED_NUMBERS.has(number) ? '#bb2737' : '#202633';
}

export default function RouletteBoard({
  selectedNumber = null,
  onSelectNumber,
  disabled = false,
}) {
  const cellWidth = 62;
  const cellHeight = 52;
  const gap = 4;

  const handleSelect = (number) => {
    if (!disabled && onSelectNumber) {
      onSelectNumber(number);
    }
  };

  return (
    <div className="roulette-board-container">
      <h3>Tapete de ruleta</h3>
      <p>Selecciona un número del 0 al 36.</p>

      <svg
        viewBox="0 0 214 836"
        role="group"
        aria-label="Tapete numérico de ruleta"
        style={{
          width: '100%',
          maxWidth: '350px',
          height: 'auto',
          display: 'block',
          margin: '0 auto',
        }}
      >
        {NUMBERS.map((number) => {
          const isZero = number === 0;
          const column = isZero ? 0 : (number - 1) % 3;
          const row = isZero ? 0 : Math.floor((number - 1) / 3) + 1;

          const x = isZero ? 4 : 4 + column * (cellWidth + gap);
          const y = 4 + row * (cellHeight + gap);
          const width = isZero
            ? cellWidth * 3 + gap * 2
            : cellWidth;

          const selected = selectedNumber === number;

          return (
            <g
              key={number}
              role="button"
              tabIndex={disabled ? -1 : 0}
              aria-label={`Número ${number}`}
              aria-pressed={selected}
              aria-disabled={disabled}
              onClick={() => handleSelect(number)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  handleSelect(number);
                }
              }}
              style={{
                cursor: disabled ? 'not-allowed' : 'pointer',
                outline: 'none',
              }}
            >
              <rect
                x={x}
                y={y}
                width={width}
                height={cellHeight}
                rx="6"
                fill={getNumberColor(number)}
                stroke={selected ? '#ffd21a' : '#c9a227'}
                strokeWidth={selected ? 5 : 2}
                opacity={disabled ? 0.5 : 1}
              />

              <text
                x={x + width / 2}
                y={y + cellHeight / 2}
                fill="white"
                fontSize="20"
                fontWeight="bold"
                textAnchor="middle"
                dominantBaseline="middle"
                pointerEvents="none"
              >
                {number}
              </text>
            </g>
          );
        })}
      </svg>

      {selectedNumber !== null && (
        <p role="status">
          Número seleccionado: <strong>{selectedNumber}</strong>
        </p>
      )}
    </div>
  );
}
