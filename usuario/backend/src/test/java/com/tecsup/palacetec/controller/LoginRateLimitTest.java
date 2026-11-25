package com.tecsup.palacetec.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tecsup.palacetec.ApiTest;
import com.tecsup.palacetec.Browser;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "palacetec.web.login-rate-limit=2")
class LoginRateLimitTest extends ApiTest {

    @Test
    void theIpIsLimitedEvenWithUnknownEmailsAndAcrossRoutes() throws Exception {
        Browser browser = browser().from("198.51.100.7");
        browser.login("unknown@example.test", "wrong").andExpect(status().isUnauthorized());
        browser.create("/api/v1/auth/refresh", Map.of()).andExpect(status().isUnauthorized());
        browser.login("other@example.test", "wrong").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail", Matchers.matchesPattern(
                        "Request was throttled\\. Expected available in \\d+ seconds?\\.")))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(header().exists("Retry-After"));
        // El límite va antes que el CSRF y no confía en X-Forwarded-For.
        mvc.perform(post("/api/v1/auth/refresh").header("X-Forwarded-For", "203.0.113.99").with(request -> {
            request.setRemoteAddr("198.51.100.7");
            return request;
        })).andExpect(status().isTooManyRequests());
        // La app móvil también está limitada, por su propia IP.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}").with(request -> {
            request.setRemoteAddr("198.51.100.7");
            return request;
        })).andExpect(status().isTooManyRequests());
        browser().from("198.51.100.8").login("unknown@example.test", "wrong").andExpect(status().isUnauthorized());
    }
}
