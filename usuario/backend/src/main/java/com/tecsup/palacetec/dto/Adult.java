package com.tecsup.palacetec.dto;

import com.tecsup.palacetec.config.TimeConfig;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Clock;
import java.time.LocalDate;

/** 18 años cumplidos según la fecha de hoy en Lima. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = Adult.Validator.class)
public @interface Adult {

    String message() default "Debes tener al menos 18 años cumplidos.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<Adult, LocalDate> {

        private final Clock clock;

        public Validator(Clock clock) {
            this.clock = clock;
        }

        @Override
        public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
            return value == null || isAdult(value, LocalDate.now(clock.withZone(TimeConfig.LIMA)));
        }

        /** Como Django: quien nació un 29 de febrero cumple 18 el 1 de marzo si el año no es bisiesto. */
        static boolean isAdult(LocalDate birthDate, LocalDate today) {
            int age = today.getYear() - birthDate.getYear();
            boolean hadBirthday = today.getMonthValue() > birthDate.getMonthValue()
                    || (today.getMonthValue() == birthDate.getMonthValue()
                    && today.getDayOfMonth() >= birthDate.getDayOfMonth());
            return (hadBirthday ? age : age - 1) >= 18;
        }
    }
}
