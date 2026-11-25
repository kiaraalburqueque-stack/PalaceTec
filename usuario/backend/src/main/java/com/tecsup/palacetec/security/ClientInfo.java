package com.tecsup.palacetec.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;

/**
 * Desde dónde se inició sesión. Se usa la IP de la conexión y no X-Forwarded-For, que el cliente puede falsificar.
 *
 * @param address null si no se conoce
 */
public record ClientInfo(InetAddress address, String userAgent) {

    private static final int MAX_USER_AGENT = 255;
    private static final int IPV6_GROUPS = 8;

    public static ClientInfo of(HttpServletRequest request) {
        String agent = request.getHeader(HttpHeaders.USER_AGENT);
        String text = agent == null ? "" : agent;
        return new ClientInfo(parse(request.getRemoteAddr()),
                text.substring(0, Math.min(text.length(), MAX_USER_AGENT)));
    }

    private static InetAddress parse(String address) {
        if (address == null || address.isEmpty()) {
            return null;
        }
        try {
            // La zona (fe80::1%eth0) depende de cada equipo y PostgreSQL no la guarda.
            return InetAddress.getByName(address.split("%")[0]);
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    /** Texto de la IP; IPv6 en su forma corta (::1), como la muestra Django. */
    public String ip() {
        if (address == null) {
            return "desconocida";
        }
        return address instanceof Inet6Address ? shortIpv6(address.getAddress()) : address.getHostAddress();
    }

    /** Comprime la secuencia más larga de grupos en cero. */
    private static String shortIpv6(byte[] bytes) {
        int[] groups = new int[IPV6_GROUPS];
        for (int i = 0; i < IPV6_GROUPS; i++) {
            groups[i] = (bytes[2 * i] & 0xFF) << 8 | bytes[2 * i + 1] & 0xFF;
        }
        int bestStart = -1;
        int bestLength = 1;
        for (int start = 0; start < IPV6_GROUPS; start++) {
            int end = start;
            while (end < IPV6_GROUPS && groups[end] == 0) {
                end++;
            }
            if (end - start > bestLength) {
                bestStart = start;
                bestLength = end - start;
            }
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < IPV6_GROUPS; i++) {
            if (i == bestStart) {
                text.append("::");
                i += bestLength - 1;
            } else {
                text.append(text.isEmpty() || text.toString().endsWith(":") ? "" : ":")
                        .append(Integer.toHexString(groups[i]));
            }
        }
        return text.toString();
    }

    /** "Chrome en Windows", a partir del User-Agent. */
    public String device() {
        String agent = userAgent.toLowerCase(Locale.ROOT);
        String browser = first(agent, "navegador desconocido", Map.entry("edg/", "Edge"),
                Map.entry("chrome/", "Chrome"), Map.entry("firefox/", "Firefox"), Map.entry("safari/", "Safari"));
        String system = first(agent, "sistema desconocido", Map.entry("windows", "Windows"),
                Map.entry("android", "Android"), Map.entry("iphone", "iPhone"), Map.entry("mac os", "macOS"),
                Map.entry("linux", "Linux"));
        return browser + " en " + system;
    }

    @SafeVarargs
    private static String first(String agent, String otherwise, Map.Entry<String, String>... names) {
        for (Map.Entry<String, String> name : names) {
            if (agent.contains(name.getKey())) {
                return name.getValue();
            }
        }
        return otherwise;
    }
}
