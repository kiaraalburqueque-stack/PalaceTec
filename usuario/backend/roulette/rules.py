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

# HU61: blackjack y deportes simulados forman parte del catálogo aunque no usen salas.
BLACKJACK_RULES = (
    "Blackjack con una baraja de 52 cartas que se mezcla en cada mano con el generador seguro del servidor. "
    "El As vale 1 u 11 y las figuras 10. Pide carta o plántate; la banca se planta en 17, incluido el 17 suave. "
    "No hay dividir, doblar, rendirse ni seguro. Apuesta de 10 a 500 créditos. "
    "RTP aproximado con estrategia básica. Créditos virtuales, sin dinero real."
)

BLACKJACK_PAYOUTS = [
    {"bet": "Victoria", "description": "Tu mano supera a la banca sin pasarse de 21", "pays": "1 a 1", "probability": ""},
    {"bet": "Blackjack natural", "description": "As y carta de valor 10 en las dos primeras cartas", "pays": "3 a 2", "probability": "4.83 %"},
    {"bet": "Empate", "description": "Misma puntuación que la banca", "pays": "Se devuelve la apuesta", "probability": ""},
]

SPORTS_RULES = (
    "Fútbol simulado con mercado 1X2: local, empate o visitante. Los encuentros y marcadores son ficticios y los genera "
    "el servidor (0 a 4 goles por equipo). Cuotas decimales fijas: local 1.90, empate 3.20 y visitante 2.40. "
    "El RTP indicado corresponde a la mejor selección (visitante). Apuesta de 10 a 500 créditos. "
    "Créditos virtuales, sin dinero real."
)

SPORTS_PAYOUTS = [
    {"bet": "Local", "description": "Gana el equipo local", "pays": "Cuota 1.90", "probability": "40.00 %"},
    {"bet": "Empate", "description": "Mismo marcador al final", "pays": "Cuota 3.20", "probability": "20.00 %"},
    {"bet": "Visitante", "description": "Gana el equipo visitante", "pays": "Cuota 2.40", "probability": "40.00 %"},
]
