package com.tecsup.palacetec.service;

import com.tecsup.palacetec.entity.BetKind;
import com.tecsup.palacetec.exception.InvalidFieldsException;
import java.util.List;
import java.util.Set;

/** Ruleta europea: colores, fichas y selecciones permitidas (roulette/constants.py y serializers.py). */
public final class RouletteTable {

    public static final List<Integer> CHIP_VALUES = List.of(10, 50, 100, 500);
    public static final int MAX_NUMBER = 36;

    private static final Set<Integer> RED_NUMBERS =
            Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    private RouletteTable() {
    }

    public static String color(int number) {
        if (number == 0) {
            return "verde";
        }
        return RED_NUMBERS.contains(number) ? "rojo" : "negro";
    }

    public static void validateSelection(BetKind kind, String selection) {
        String error = switch (kind) {
            case STRAIGHT_UP -> isNumber(selection) ? null : "El pleno debe ser un numero del 0 al 36.";
            case COLOR -> Set.of("rojo", "negro").contains(selection) ? null : "El color debe ser rojo o negro.";
            case PARITY -> Set.of("par", "impar").contains(selection) ? null : "La paridad debe ser par o impar.";
            case DOZEN, COLUMN -> Set.of("1", "2", "3").contains(selection) ? null : "Selecciona 1, 2 o 3.";
        };
        if (error != null) {
            throw InvalidFieldsException.field("selection", error);
        }
    }

    /** "0" a "36", sin ceros a la izquierda ni espacios. */
    private static boolean isNumber(String selection) {
        return selection.matches("[0-9]|[1-9][0-9]") && Integer.parseInt(selection) <= MAX_NUMBER;
    }
}
