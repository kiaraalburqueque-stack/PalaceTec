package com.tecsup.palacetec.service;

import com.tecsup.palacetec.dto.ProfileResponse;
import com.tecsup.palacetec.entity.User;
import com.tecsup.palacetec.exception.InvalidFieldsException;
import com.tecsup.palacetec.media.AvatarImages;
import com.tecsup.palacetec.media.MediaStorage;
import com.tecsup.palacetec.security.PlayerPrincipal;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/** Foto propia del jugador (HU65). */
@Service
public class AvatarService {

    private final AvatarImages images;
    private final MediaStorage media;
    private final SessionService sessions;
    private final TransactionTemplate transaction;

    public AvatarService(AvatarImages images, MediaStorage media, SessionService sessions,
                         TransactionTemplate transaction) {
        this.images = images;
        this.media = media;
        this.sessions = sessions;
        this.transaction = transaction;
    }

    /** La imagen se procesa antes de bloquear al usuario, para no retener su fila mientras tanto. */
    public ProfileResponse upload(PlayerPrincipal player, MultipartFile upload) throws IOException {
        if (upload == null || upload.getOriginalFilename() == null || upload.getOriginalFilename().isEmpty()) {
            throw InvalidFieldsException.field("avatar", "Selecciona una imagen JPG, PNG o WEBP.");
        }
        byte[] image = images.toAvatar(upload);
        return transaction.execute(status -> {
            User user = sessions.lock(player);
            String previous = user.getAvatarUrl();
            String current = save(user, image);
            media.replaceAvatar(previous, current);
            user.updateProfile(null, current, null, null, null);
            return ProfileResponse.from(user);
        });
    }

    private String save(User user, byte[] image) {
        try {
            return media.saveAvatar(user.getId(), image);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
