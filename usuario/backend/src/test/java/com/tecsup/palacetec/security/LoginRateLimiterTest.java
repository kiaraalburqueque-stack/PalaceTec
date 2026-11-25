package com.tecsup.palacetec.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tecsup.palacetec.config.PalaceTecProperties;
import com.tecsup.palacetec.exception.ThrottledException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

    private final Clock clock = mock(Clock.class);
    private final LoginRateLimiter limiter = new LoginRateLimiter(new PalaceTecProperties(
            new PalaceTecProperties.Jwt("x".repeat(32), Duration.ofHours(8)), "hmac", "http://localhost:8000",
            List.of("http://localhost:5173"), new PalaceTecProperties.Web(false, 2)), clock);

    private void checkAt(long millis) {
        when(clock.millis()).thenReturn(millis);
        limiter.check("10.0.0.1");
    }

    @Test
    void theWindowSlidesOneMinute() {
        checkAt(0);
        checkAt(30_000);
        assertThatThrownBy(() -> checkAt(30_500)).isInstanceOfSatisfying(ThrottledException.class, error -> {
            assertThat(error.getRetryAfter()).isEqualTo(30);
            assertThat(error.getMessage()).isEqualTo("Request was throttled. Expected available in 30 seconds.");
        });
        assertThatThrownBy(() -> checkAt(59_500)).hasMessage(
                "Request was throttled. Expected available in 1 second.");
        // Las rechazadas no alargan la espera.
        checkAt(60_000);
        assertThatThrownBy(() -> checkAt(60_001)).isInstanceOf(ThrottledException.class);
        checkAt(90_000);
    }

    @Test
    void eachAddressHasItsOwnCount() {
        checkAt(0);
        checkAt(0);
        limiter.check("10.0.0.2");
        assertThatThrownBy(() -> checkAt(0)).isInstanceOf(ThrottledException.class);
    }
}
