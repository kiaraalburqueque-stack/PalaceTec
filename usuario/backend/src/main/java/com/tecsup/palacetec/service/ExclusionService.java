package com.tecsup.palacetec.service;

import com.tecsup.palacetec.config.PalaceTecProperties;
import com.tecsup.palacetec.entity.User;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.repository.ExcludedPersonRepository;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Registro de excluidos, suspensión y autoexclusión. */
@Service
public class ExclusionService {

    private static final String KEY_SALT = "palacetec.exclusions";

    private final ExcludedPersonRepository excluded;
    private final SecretKeySpec key;
    private final Clock clock;

    public ExclusionService(ExcludedPersonRepository excluded, PalaceTecProperties properties, Clock clock) {
        this.excluded = excluded;
        this.key = deriveKey(properties.exclusionHmacKey());
        this.clock = clock;
    }

    public void ensureDniAllowed(String dni) {
        if (excluded.existsByDniHash(digest(dni))) {
            throw accessNotAllowed();
        }
    }

    public void ensureUserAllowed(User user) {
        ensureDniAllowed(user.getDni());
        boolean selfExcluded = user.getSelfExcludedUntil() != null
                && user.getSelfExcludedUntil().isAfter(clock.instant());
        if (!user.isActive() || selfExcluded) {
            throw accessNotAllowed();
        }
    }

    /** Equivale a salted_hmac(KEY_SALT, dni, secret=EXCLUSION_HMAC_KEY, algorithm="sha256") de Django. */
    public String digest(String dni) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(dni.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static SecretKeySpec deriveKey(String secret) {
        try {
            byte[] derived = MessageDigest.getInstance("SHA-256")
                    .digest((KEY_SALT + secret).getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(derived, "HmacSHA256");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** Mensaje genérico: no revela el motivo de la exclusión. */
    private static ApiException accessNotAllowed() {
        return new ApiException(HttpStatus.FORBIDDEN, "access_not_allowed", "No es posible habilitar el acceso.");
    }
}
