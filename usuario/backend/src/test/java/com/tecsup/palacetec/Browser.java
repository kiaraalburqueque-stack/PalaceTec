package com.tecsup.palacetec;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Navegador con el frontend React: guarda cookies, envía Origin y pide el token CSRF antes de cada escritura. */
public class Browser {

    public static final String ORIGIN = "http://localhost:5173";

    private final MockMvc mvc;
    private final ObjectMapper mapper;
    private final Map<String, Cookie> cookies = new LinkedHashMap<>();
    private String address = "127.0.0.1";
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0 Safari/537.36";
    private boolean csrf = true;

    public Browser(MockMvc mvc, ObjectMapper mapper) {
        this.mvc = mvc;
        this.mapper = mapper;
    }

    public Browser from(String newAddress) {
        address = newAddress;
        return this;
    }

    public Browser using(String newUserAgent) {
        userAgent = newUserAgent;
        return this;
    }

    /** Deja de enviar X-CSRFToken. */
    public Browser withoutCsrf() {
        csrf = false;
        return this;
    }

    public String cookie(String name) {
        return cookies.containsKey(name) ? cookies.get(name).getValue() : null;
    }

    public void setCookie(String name, String value) {
        cookies.put(name, new Cookie(name, value));
    }

    public ResultActions read(String path) throws Exception {
        return send(get(path));
    }

    public ResultActions create(String path, Object body) throws Exception {
        return write(json(post(path), body));
    }

    public ResultActions update(String path, Object body) throws Exception {
        return write(json(put(path), body));
    }

    public ResultActions login(String email, String password) throws Exception {
        return create("/api/v1/auth/login", Map.of("email", email, "password", password));
    }

    /** Escritura con el token CSRF, sea cual sea su tipo de contenido. */
    public ResultActions write(MockHttpServletRequestBuilder request) throws Exception {
        if (csrf) {
            byte[] answer = read("/api/v1/auth/csrf").andReturn().getResponse().getContentAsByteArray();
            request.header("X-CSRFToken", mapper.readTree(answer).get("csrfToken").asText());
        }
        return send(request);
    }

    public ResultActions send(MockHttpServletRequestBuilder request) throws Exception {
        request.header("Origin", ORIGIN).header("User-Agent", userAgent).with(built -> {
            built.setRemoteAddr(address);
            return built;
        });
        String path = request.buildRequest(new org.springframework.mock.web.MockServletContext()).getRequestURI();
        // La cookie de renovación tiene Path=/api/v1/auth/.
        cookies.values().stream()
                .filter(cookie -> !cookie.getName().equals("refresh_token") || path.startsWith("/api/v1/auth/"))
                .forEach(request::cookie);
        ResultActions result = mvc.perform(request);
        for (Cookie cookie : result.andReturn().getResponse().getCookies()) {
            if (cookie.getMaxAge() == 0) {
                cookies.remove(cookie.getName());
            } else {
                cookies.put(cookie.getName(), new Cookie(cookie.getName(), cookie.getValue()));
            }
        }
        return result;
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String text ? text.getBytes() : mapper.writeValueAsBytes(body));
    }
}
