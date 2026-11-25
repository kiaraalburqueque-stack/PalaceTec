package com.tecsup.palacetec.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Reglas de un CharField de Django REST Framework. Como allí, se informan todas las que no se cumplen. */
public final class TextRule {

    record Check(Predicate<String> accepts, String message) {
    }

    private static final String PATTERN_MESSAGE = "This value does not match the required pattern.";

    final boolean required;
    boolean trim = true;
    boolean allowBlank;
    boolean email;
    int maxLength = Integer.MAX_VALUE;
    final List<Check> checks = new ArrayList<>();

    private TextRule(boolean required) {
        this.required = required;
    }

    public static TextRule required() {
        return new TextRule(true);
    }

    public static TextRule optional() {
        return new TextRule(false);
    }

    /** trim_whitespace=False: los espacios son parte del valor (contraseñas). */
    public TextRule keepSpaces() {
        trim = false;
        return this;
    }

    public TextRule allowBlank() {
        allowBlank = true;
        return this;
    }

    /** Longitud en caracteres Unicode, como len() de Python. */
    public TextRule max(int length) {
        maxLength = length;
        return this;
    }

    public TextRule email() {
        email = true;
        return this;
    }

    public TextRule pattern(String regex) {
        Pattern pattern = Pattern.compile(regex);
        checks.add(new Check(value -> pattern.matcher(value).matches(), PATTERN_MESSAGE));
        return this;
    }
}
