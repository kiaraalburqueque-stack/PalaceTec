package com.tecsup.palacetec.dto;

import java.util.Arrays;
import java.util.Locale;

/** Categorías del catálogo, en el orden de las pestañas. El nombre es el valor guardado en la base. */
public enum GameCategory {

    RULETA("Ruleta"), CARTAS("Cartas"), DEPORTES("Deportes virtuales"), OTROS("Otros");

    private final String label;

    GameCategory(String label) {
        this.label = label;
    }

    /** null si no existe. */
    public static GameCategory ofSlug(String slug) {
        return Arrays.stream(values()).filter(category -> category.slug().equals(slug)).findFirst().orElse(null);
    }

    /** Una categoría desconocida se muestra en "otros". */
    public static GameCategory ofCode(String code) {
        return Arrays.stream(values()).filter(category -> category.name().equals(code)).findFirst().orElse(OTROS);
    }

    public String slug() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String label() {
        return label;
    }
}
