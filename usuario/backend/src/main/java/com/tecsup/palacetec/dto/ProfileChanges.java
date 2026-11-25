package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Campos editables del perfil, ya validados como ProfileSerializer. Los que no vinieron quedan en null. */
public record ProfileChanges(String nickname, String avatarUrl, String firstName, String lastName, String phone) {

    public static final String NICKNAME_TAKEN = "Este apodo ya esta registrado.";

    private static final Set<String> AVATARS = IntStream.rangeClosed(1, 6)
            .mapToObj("/avatars/avatar-%d.svg"::formatted).collect(Collectors.toUnmodifiableSet());
    private static final String NAME_SEPARATORS = " .'-";
    private static final int MAX_PHONE = 20;

    /**
     * @param currentAvatar el propio avatar, que puede ser una foto subida, se puede volver a enviar
     * @param nicknameTaken si el apodo, en minúsculas, es de otro jugador
     */
    public static ProfileChanges from(JsonNode body, String currentAvatar, Predicate<String> nicknameTaken) {
        RequestFields fields = RequestFields.of(body);
        String nickname = fields.text("nickname", TextRule.optional().pattern("[a-zA-Z0-9_]{3,30}"));
        if (nickname != null) {
            nickname = nickname.toLowerCase(Locale.ROOT);
            if (nicknameTaken.test(nickname)) {
                fields.error("nickname", NICKNAME_TAKEN);
            }
        }
        String avatar = fields.text("avatar_url", TextRule.optional().max(100));
        if (avatar != null && !AVATARS.contains(avatar) && !avatar.equals(currentAvatar)) {
            fields.error("avatar_url", "Elige uno de los avatares disponibles o sube tu foto.");
        }
        String firstName = fields.text("first_name", TextRule.optional().allowBlank().max(80));
        String lastName = fields.text("last_name", TextRule.optional().allowBlank().max(80));
        // El patrón admite 21 caracteres con el signo +, uno más que la columna.
        String phone = fields.text("phone", TextRule.optional().allowBlank().max(MAX_PHONE)
                .pattern("\\+?[0-9 ()-]{7,20}"));
        fields.throwIfInvalid();
        rejectIdentity(fields);
        checkName(fields, "first_name", firstName);
        checkName(fields, "last_name", lastName);
        return new ProfileChanges(nickname, avatar, firstName, lastName, phone);
    }

    private static void rejectIdentity(RequestFields fields) {
        for (String field : new String[] {"email", "dni"}) {
            if (fields.has(field)) {
                fields.error(field, "Este campo no es editable.");
            }
        }
        fields.throwIfInvalid();
    }

    /** Como en Django, se informa solo el primer nombre inválido. */
    private static void checkName(RequestFields fields, String field, String value) {
        boolean valid = value == null || value.codePoints().allMatch(
                point -> Character.isLetter(point) || NAME_SEPARATORS.indexOf(point) >= 0);
        if (!valid) {
            fields.error(field, "Usa letras y separadores de nombre validos.");
            fields.throwIfInvalid();
        }
    }
}
