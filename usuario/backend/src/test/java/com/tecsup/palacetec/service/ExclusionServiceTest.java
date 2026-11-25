package com.tecsup.palacetec.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tecsup.palacetec.ApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ExclusionServiceTest extends ApiTest {

    @Autowired
    private ExclusionService exclusions;

    /** Valor de salted_hmac("palacetec.exclusions", "00000000", ...) en Django con la misma clave ficticia. */
    @Test
    void dniFingerprintMatchesDjango() {
        assertThat(exclusions.digest("00000000"))
                .isEqualTo("7509976daf2597ef818f7c399cb5c01933f6900b7551bba57d501d5090fd01aa");
    }
}
