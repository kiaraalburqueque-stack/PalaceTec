package com.tecsup.palacetec.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Reglas de RegisterSerializer.validate_password: 8 caracteres, una mayúscula y un símbolo. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StrongPassword.Validator.class)
public @interface StrongPassword {

    String message() default "Contraseña inválida.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<StrongPassword, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null || value.isEmpty()) {
                return true; // Lo informa @NotEmpty.
            }
            String error = problem(value);
            if (error == null) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(error).addConstraintViolation();
            return false;
        }

        /** Primer requisito que no se cumple, o null. */
        public static String problem(String value) {
            if (value.codePointCount(0, value.length()) < 8) {
                return "La contraseña debe tener al menos 8 caracteres.";
            }
            if (value.codePoints().noneMatch(Character::isUpperCase)) {
                return "La contraseña debe incluir una mayúscula.";
            }
            if (value.codePoints().noneMatch(Validator::isSymbol)) {
                return "La contraseña debe incluir un símbolo.";
            }
            return null;
        }

        private static boolean isSymbol(int character) {
            int type = Character.getType(character);
            boolean alphanumeric = Character.isLetterOrDigit(character)
                    || type == Character.LETTER_NUMBER || type == Character.OTHER_NUMBER;
            return !alphanumeric && !Character.isWhitespace(character) && !Character.isSpaceChar(character);
        }
    }
}
