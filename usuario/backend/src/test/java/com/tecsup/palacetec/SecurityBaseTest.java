package com.tecsup.palacetec;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecurityBaseTest extends ApiTest {

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/users/me", "/api/v1/wallet/summary", "/api/v1/games", "/api/v1/rooms",
            "/api/v1/rooms/1/state", "/api/v1/users/bets-history", "/api/v1/responsible-gaming/daily-limit"})
    void protectedRoutesRequireLogin(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"api\""))
                .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @Test
    void writesRequireLoginAndNotCsrf() throws Exception {
        mvc.perform(post("/api/v1/rooms/1/bets")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/responsible-gaming/daily-limit")).andExpect(status().isUnauthorized());
    }

    @Test
    void corsAllowsOnlyThePlayerFrontend() throws Exception {
        mvc.perform(options("/api/v1/rooms/1/bets").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/v1/rooms/1/bets").header("Origin", "http://otro-sitio.test")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
