"""HU68: filtro que impide dar estrategias para ganar o animar a apostar más."""
from roulette.catalog import normalize

# Frases ya normalizadas (minúsculas y sin tildes).
STRATEGY_PATTERNS = (
    "estrategia", "truco", "trucos", "martingala", "fibonacci", "sistema para ganar", "metodo para ganar",
    "como gano", "como ganar", "como puedo ganar", "ganar siempre", "ganar seguro", "asegurar ganancia",
    "asegurar el premio", "nunca perder", "no perder", "recuperar lo perdido", "recuperar mis perdidas",
    "recuperar perdidas", "recuperar lo que perdi", "apostar mas", "subir la apuesta", "duplicar la apuesta",
    "doblar la apuesta", "que numero va a salir", "que numero saldra", "numero ganador", "predecir", "prediccion",
    "hackear", "hack", "patron de la ruleta",
)

GUARD_REPLY = (
    "No puedo darte estrategias, trucos ni consejos para ganar o para apostar más. Cada resultado lo decide un "
    "generador aleatorio del servidor: las rondas son independientes y ningún sistema cambia la ventaja de la casa. "
    "Juega solo por entretenimiento, con créditos virtuales, y define tu límite diario en Mis límites. "
    "Si sientes que necesitas recuperar lo perdido, es un buen momento para tomar una pausa."
)

RESPONSIBLE_REMINDER = "Juega con responsabilidad: son créditos virtuales y puedes fijar tu límite diario en Mis límites."


def asks_for_strategy(text):
    plain = f" {normalize(text)} "
    return any(f" {pattern} " in plain or (len(pattern) > 6 and pattern in plain) for pattern in STRATEGY_PATTERNS)
