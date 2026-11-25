package com.tecsup.palacetec.config;

import com.tecsup.palacetec.media.MediaStorage;
import com.tecsup.palacetec.security.CsrfGuard;
import com.tecsup.palacetec.security.JwtAuthenticationFilter;
import com.tecsup.palacetec.security.JwtService;
import com.tecsup.palacetec.security.RestAuthenticationEntryPoint;
import com.tecsup.palacetec.security.SessionCookies;
import com.tecsup.palacetec.security.WebTokenService;
import com.tecsup.palacetec.service.SessionService;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    public static final String REGISTER_PATH = "/api/v1/auth/register";
    public static final String LOGIN_PATH = "/api/v1/auth/login";
    public static final String LOGOUT_PATH = "/api/v1/auth/logout";
    public static final String REFRESH_PATH = "/api/v1/auth/refresh";
    public static final String CSRF_PATH = "/api/v1/auth/csrf";
    public static final String FORGOT_PASSWORD_PATH = "/api/v1/auth/forgot-password";
    public static final String RESET_PASSWORD_PATH = "/api/v1/auth/reset-password";
    public static final String NOT_ME_PATH = "/api/v1/auth/not-me";

    private static final List<String> PUBLIC_POSTS = List.of(REGISTER_PATH, LOGIN_PATH, LOGOUT_PATH, REFRESH_PATH,
            FORGOT_PASSWORD_PATH, RESET_PASSWORD_PATH, NOT_ME_PATH);
    /** authentication_classes = [] en Django: aquí no se mira el token. Sus POST se protegen con @AuthGuard. */
    public static final Set<String> PUBLIC_PATHS = Set.of(REGISTER_PATH, LOGIN_PATH, LOGOUT_PATH, REFRESH_PATH,
            CSRF_PATH, FORGOT_PASSWORD_PATH, RESET_PASSWORD_PATH, NOT_ME_PATH);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, RestAuthenticationEntryPoint entryPoint,
                                                   JwtService jwt, WebTokenService webTokens, SessionCookies cookies,
                                                   CsrfGuard csrf, SessionService sessions) throws Exception {
        return http
                // El CSRF lo revisa CsrfGuard, con el formato de Django: en las rutas públicas con @AuthGuard y en las
                // demás al autenticar con la cookie. Con Bearer no hace falta.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint))
                .addFilterBefore(new JwtAuthenticationFilter(jwt, webTokens, cookies, csrf, sessions, entryPoint),
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, PUBLIC_POSTS.toArray(String[]::new)).permitAll()
                        .requestMatchers(HttpMethod.GET, CSRF_PATH, MediaStorage.URL + "**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(PalaceTecProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.corsAllowedOrigins());
        cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("DELETE", "GET", "OPTIONS", "PATCH", "POST", "PUT"));
        cors.setAllowedHeaders(List.of("Accept", "Authorization", "Content-Type", CsrfGuard.HEADER,
                "X-Requested-With"));
        cors.setExposedHeaders(List.of("Content-Disposition", "Retry-After"));
        cors.setMaxAge(Duration.ofDays(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
