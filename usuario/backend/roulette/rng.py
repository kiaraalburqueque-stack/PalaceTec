"""Generador del resultado de una ruleta europea."""
import secrets


def spin():
    """Devuelve un entero entre 0 y 36 usando el generador seguro del sistema."""
    return secrets.randbelow(37)
