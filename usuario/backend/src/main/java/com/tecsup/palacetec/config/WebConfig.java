package com.tecsup.palacetec.config;

import com.tecsup.palacetec.security.AuthGuardInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthGuardInterceptor authGuard;

    public WebConfig(AuthGuardInterceptor authGuard) {
        this.authGuard = authGuard;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authGuard);
    }
}
