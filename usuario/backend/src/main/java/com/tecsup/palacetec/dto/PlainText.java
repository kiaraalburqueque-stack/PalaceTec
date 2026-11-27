package com.tecsup.palacetec.dto;

import java.text.Normalizer;
import java.util.Locale;

/** Textos comparables: el catálogo y el asistente no distinguen tildes ni mayúsculas. */
public final class PlainText {

    private PlainText() {
    }

    /** Minúsculas, sin tildes y con un solo espacio entre palabras. */
    public static String normalize(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
        return plain.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT).strip().replaceAll("\\s+", " ");
    }

    /** Palabras de un texto ya normalizado, sin signos y entre espacios, para buscarlas completas. */
    public static String words(String normalized) {
        return " " + normalized.replaceAll("[^\\p{L}\\p{N}]+", " ").strip() + " ";
    }
}
