package com.tecsup.palacetec.security;

import com.tecsup.palacetec.config.AssistantProperties;
import com.tecsup.palacetec.exception.AssistantThrottledException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** AssistantRateThrottle de Django: mensajes por minuto y por jugador, antes de leer el cuerpo. Consultar no cuenta. */
@Component
public class AssistantRateInterceptor implements HandlerInterceptor {

    private final SlidingWindow window;

    public AssistantRateInterceptor(AssistantProperties properties, Clock clock) {
        this.window = new SlidingWindow(properties.rateLimit(), clock);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (HttpMethod.POST.matches(request.getMethod())
                && authentication != null && authentication.getPrincipal() instanceof PlayerPrincipal player) {
            long wait = window.register(player.userId().toString());
            if (wait > 0) {
                throw new AssistantThrottledException(wait);
            }
        }
        return true;
    }
}
