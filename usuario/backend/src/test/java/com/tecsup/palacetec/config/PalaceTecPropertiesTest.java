package com.tecsup.palacetec.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/** application.yml sin variables de entorno ni application-local.yml: la aplicación no debe aceptar claves ausentes. */
class PalaceTecPropertiesTest {

    private static Set<String> problems(Map<String, Object> variables) throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().forEach(source -> environment.getPropertySources().remove(source.getName()));
        environment.getPropertySources().addFirst(new MapPropertySource("variables", variables));
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);
        PalaceTecProperties properties = Binder.get(environment).bind("palacetec", PalaceTecProperties.class).get();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator().validate(properties).stream()
                    .map(violation -> violation.getPropertyPath().toString()).collect(Collectors.toSet());
        }
    }

    @Test
    void missingSecretsAreRejected() throws Exception {
        assertThat(problems(Map.of())).containsExactlyInAnyOrder("jwt.secret", "exclusionHmacKey");
        assertThat(problems(Map.of("JWT_SECRET", "corta", "EXCLUSION_HMAC_KEY", " ")))
                .containsExactlyInAnyOrder("jwt.secret", "exclusionHmacKey");
    }

    @Test
    void completeConfigurationIsAccepted() throws Exception {
        assertThat(problems(Map.of("JWT_SECRET", "clave-ficticia-solo-para-pruebas-0123456789",
                "EXCLUSION_HMAC_KEY", "ficticia"))).isEmpty();
    }
}
