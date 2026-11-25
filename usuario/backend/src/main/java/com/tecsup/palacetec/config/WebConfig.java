package com.tecsup.palacetec.config;

import com.tecsup.palacetec.media.MediaStorage;
import com.tecsup.palacetec.security.AssistantRateInterceptor;
import com.tecsup.palacetec.security.AuthGuardInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static final String ASSISTANT_CHAT_PATH = "/api/v1/assistant/chat";

    private final AuthGuardInterceptor authGuard;
    private final AssistantRateInterceptor assistantRate;
    private final MediaStorage media;

    public WebConfig(AuthGuardInterceptor authGuard, AssistantRateInterceptor assistantRate, MediaStorage media) {
        this.authGuard = authGuard;
        this.assistantRate = assistantRate;
        this.media = media;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authGuard);
        registry.addInterceptor(assistantRate).addPathPatterns(ASSISTANT_CHAT_PATH);
    }

    /** React pide las fotos al mismo servidor de la API: avatar_url empieza con /media/. */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = media.root().toUri().toString();
        registry.addResourceHandler(MediaStorage.URL + "**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/")
                .setCacheControl(CacheControl.noCache());
    }
}
