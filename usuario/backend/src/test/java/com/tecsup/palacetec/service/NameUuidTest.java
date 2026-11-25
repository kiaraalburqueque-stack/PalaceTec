package com.tecsup.palacetec.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class NameUuidTest {

    /** Valor calculado con uuid.uuid5 de Python. */
    @Test
    void matchesPythonUuid5() {
        assertThat(NameUuid.version5(UUID.fromString("f177dd7c-431d-4280-8260-b62e150c306a"), "roulette:1:1"))
                .hasToString("eb561b6e-ba00-55a0-846c-0aa985529b88");
    }
}
