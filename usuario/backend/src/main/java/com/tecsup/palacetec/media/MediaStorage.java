package com.tecsup.palacetec.media;

import com.tecsup.palacetec.config.PalaceTecProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Carpeta media que se comparte con Django: users.avatar_url vale para los dos backends. */
@Component
public class MediaStorage {

    /** MEDIA_URL de Django. */
    public static final String URL = "/media/";

    private static final Logger log = LoggerFactory.getLogger(MediaStorage.class);
    private static final String AVATARS = "avatars/";
    private static final int RANDOM_BYTES = 6;

    private final SecureRandom random = new SecureRandom();
    private final Path root;

    public MediaStorage(PalaceTecProperties properties) {
        this.root = Path.of(properties.web().mediaRoot()).toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    /** Guarda la foto con un nombre que no se puede adivinar y devuelve su avatar_url. */
    public String saveAvatar(Long userId, byte[] image) throws IOException {
        byte[] suffix = new byte[RANDOM_BYTES];
        random.nextBytes(suffix);
        String name = AVATARS + "user_" + userId + "_" + HexFormat.of().formatHex(suffix) + ".webp";
        Path file = root.resolve(name);
        Files.createDirectories(file.getParent());
        Files.write(file, image);
        return URL + name;
    }

    /** Al confirmar la transacción borra la foto anterior; si se revierte, la nueva. */
    public void replaceAvatar(String previous, String current) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                deleteAvatar(status == STATUS_COMMITTED ? previous : current);
            }
        });
    }

    /** Solo borra fotos subidas, nunca los avatares predeterminados ni archivos de otra carpeta. */
    private void deleteAvatar(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith(URL + AVATARS)) {
            return;
        }
        Path file = root.resolve(avatarUrl.substring(URL.length())).normalize();
        if (!file.startsWith(root.resolve(AVATARS))) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            log.warn("No se pudo borrar {}", file, exception);
        }
    }
}
