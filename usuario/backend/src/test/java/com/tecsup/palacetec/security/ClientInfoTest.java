package com.tecsup.palacetec.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientInfoTest {

    private static ClientInfo client(String address, String agent) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(address);
        if (agent != null) {
            request.addHeader("User-Agent", agent);
        }
        return ClientInfo.of(request);
    }

    @ParameterizedTest
    @CsvSource({"127.0.0.1, 127.0.0.1", "0:0:0:0:0:0:0:1, ::1", "fe80:0:0:0:1:0:0:2%7, fe80::1:0:0:2",
            "2001:db8:0:1:0:0:0:0, 2001:db8:0:1::", "1:2:3:4:5:6:7:8, 1:2:3:4:5:6:7:8", "'', desconocida"})
    void showsTheShortAddress(String address, String expected) {
        assertThat(client(address, "x").ip()).isEqualTo(expected);
    }

    @Test
    void describesTheDeviceLikeDjango() {
        assertThat(client("::1", "Mozilla/5.0 (Windows NT 10.0) Chrome/130.0 Safari/537.36 Edg/130.0").device())
                .isEqualTo("Edge en Windows");
        assertThat(client("::1", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) Safari/604.1").device())
                .isEqualTo("Safari en iPhone");
        assertThat(client("::1", null).device()).isEqualTo("navegador desconocido en sistema desconocido");
    }
}
