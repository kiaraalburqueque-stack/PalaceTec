# Motor de ruleta europea

Esta app contiene logica pura: no consulta la base de datos, no modifica billeteras y no publica endpoints.

## Archivos para sustentar

- `__init__.py`: permite importar roulette como paquete de Python.
- `apps.py`: identifica la app ante Django.
- `../config/settings.py`: registra roulette en INSTALLED_APPS.
- `constants.py`: define conjuntos inmutables de numeros rojos, negros y verdes. Los negros son los numeros del 1 al 36 que no son rojos; el unico verde es el cero.
- `rng.py`: spin() llama secrets.randbelow(37), que produce un entero desde cero hasta 36. Usa el generador seguro del sistema operativo. No importa random.
- `payout.py`: contiene pleno, color y paridad. Reciben los datos y devuelven un Decimal; no guardan nada. Validan que el monto sea positivo y finito, que el resultado sea un entero de 0 a 36 y que la seleccion sea valida. Entradas invalidas producen ValueError. El pago incluye la apuesta original: pleno multiplica por 36; color y paridad multiplican por 2. Perder devuelve cero. El cero puede ganar en pleno, pero siempre pierde en color y paridad.
- `test_hu18.py`: verifica el uso de secrets.randbelow(37), los colores, la ausencia de importaciones de random y 37000 giros reales dentro del rango con cobertura de los 37 numeros. Esta cobertura es una comprobacion probabilistica, no una demostracion de uniformidad.
- `test_hu13.py`: verifica pleno ganado y perdido, incluido el cero, y entradas invalidas.
- `test_hu14.py`: comprueba ambos colores sobre todos los numeros, el cero y entradas invalidas.
- `test_hu15.py`: comprueba par e impar sobre todos los numeros, el cero y entradas invalidas.
- `README.md`: documenta el contrato de las funciones y como comprobarlo.

## Ejemplos

```python
from roulette.rng import spin
from roulette.payout import pleno, color, paridad

result = spin()
pleno("10.00", 17, 17)       # Decimal("360.00")
color("10.00", "rojo", 1)   # Decimal("20.00")
paridad("10.00", "par", 0)  # Decimal("0")
```

Se recomienda entregar montos como Decimal o texto decimal para preservar la precision desde el origen. Las selecciones de color y paridad usan minusculas en espanol.

## Pruebas

Desde usuario/backend: `python manage.py test roulette`.
Tambien se pueden ejecutar sin configurar Django ni una base: `python -m unittest discover -s . -p "test_hu1*.py"` (este patron incluye las cuatro historias de roulette).
No hay modelos ni cambios de esquema, por lo que esta app no necesita migraciones.
