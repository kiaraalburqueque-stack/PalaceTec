"""HU60: reglas y tabla de pagos que se muestran antes de jugar."""
EUROPEAN_ROULETTE_RULES = (
    "Ruleta europea de 37 casillas (0 al 36). Durante 15 segundos coloca fichas de 10, 50, 100 o 500 créditos; "
    "al cerrarse la mesa («No va más») el servidor gira la ruleta con un generador aleatorio seguro. "
    "Si sale el 0, pierden todas las apuestas externas (color, paridad, docena y columna). "
    "Respeta el mínimo por apuesta y el máximo acumulado por ronda de cada sala. "
    "Créditos virtuales, sin dinero real."
)

EUROPEAN_ROULETTE_PAYOUTS = [
    {"bet": "Pleno", "description": "Un número del 0 al 36", "pays": "35 a 1", "probability": "2.70 %"},
    {"bet": "Color", "description": "Rojo o negro", "pays": "1 a 1", "probability": "48.65 %"},
    {"bet": "Paridad", "description": "Par o impar", "pays": "1 a 1", "probability": "48.65 %"},
    {"bet": "Docena", "description": "1-12, 13-24 o 25-36", "pays": "2 a 1", "probability": "32.43 %"},
    {"bet": "Columna", "description": "Una de las tres columnas", "pays": "2 a 1", "probability": "32.43 %"},
]
