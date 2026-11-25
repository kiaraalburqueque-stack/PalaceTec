package com.tecsup.palacetec.security;

import com.tecsup.palacetec.dto.RequestFields;
import com.tecsup.palacetec.entity.User;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** AUTH_PASSWORD_VALIDATORS de Django, con sus mensajes. Se usan al cambiar o restablecer la contraseña. */
@Component
public class PasswordRules {

    /** La misma lista de 20 000 contraseñas que trae Django. */
    private static final String COMMON_PASSWORDS = "common-passwords.txt.gz";
    private static final int MIN_LENGTH = 8;
    private static final double MAX_SIMILARITY = 0.7;
    private static final Pattern NOT_WORD = Pattern.compile("\\W+", Pattern.UNICODE_CHARACTER_CLASS);

    private final Set<String> common = readCommon();

    /** Todos los problemas de la contraseña, en el orden de Django. */
    public List<String> problems(String password, User user) {
        List<String> problems = new ArrayList<>();
        attributes(user).entrySet().stream().filter(attribute -> isSimilar(password, attribute.getValue())).findFirst()
                .ifPresent(attribute -> problems.add(
                        "The password is too similar to the " + attribute.getKey() + "."));
        if (length(password) < MIN_LENGTH) {
            problems.add("This password is too short. It must contain at least " + MIN_LENGTH + " characters.");
        }
        if (common.contains(RequestFields.strip(password.toLowerCase(Locale.ROOT)))) {
            problems.add("This password is too common.");
        }
        if (!password.isEmpty() && password.codePoints().allMatch(Character::isDigit)) {
            problems.add("This password is entirely numeric.");
        }
        return problems;
    }

    private static Map<String, String> attributes(User user) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("first name", user.getFirstName());
        attributes.put("last name", user.getLastName());
        attributes.put("email", user.getEmail());
        return attributes;
    }

    /** Compara con el valor completo y con cada una de sus partes (por ejemplo, las de un correo). */
    private static boolean isSimilar(String password, String value) {
        String candidate = password.toLowerCase(Locale.ROOT);
        String lower = value.toLowerCase(Locale.ROOT);
        return Stream.concat(Stream.of(NOT_WORD.split(lower)), Stream.of(lower))
                .filter(part -> !part.isEmpty() && !isMuchShorter(part, candidate))
                .anyMatch(part -> quickRatio(candidate, part) >= MAX_SIMILARITY);
    }

    private static boolean isMuchShorter(String part, String password) {
        return length(password) >= 10 * length(part) && length(part) < MAX_SIMILARITY / 2 * length(password);
    }

    /** SequenceMatcher.quick_ratio() de Python: proporción de caracteres en común, sin importar el orden. */
    private static double quickRatio(String first, String second) {
        Map<Integer, Integer> available = new HashMap<>();
        second.codePoints().forEach(point -> available.merge(point, 1, Integer::sum));
        long matches = first.codePoints().filter(point -> available.merge(point, -1, Integer::sum) >= 0).count();
        return 2.0 * matches / (length(first) + length(second));
    }

    private static int length(String text) {
        return text.codePointCount(0, text.length());
    }

    private static Set<String> readCommon() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(new ClassPathResource(COMMON_PASSWORDS).getInputStream()),
                StandardCharsets.UTF_8))) {
            return reader.lines().map(String::strip).collect(Collectors.toUnmodifiableSet());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
