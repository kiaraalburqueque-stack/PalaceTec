package com.tecsup.palacetec.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.dto.ProfileChanges;
import com.tecsup.palacetec.dto.ProfileResponse;
import com.tecsup.palacetec.entity.User;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.exception.InvalidFieldsException;
import com.tecsup.palacetec.repository.UserRepository;
import com.tecsup.palacetec.security.PlayerPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Perfil del jugador autenticado. */
@Service
public class ProfileService {

    private final UserRepository users;
    private final SessionService sessions;
    private final TransactionTemplate transaction;

    public ProfileService(UserRepository users, SessionService sessions, TransactionTemplate transaction) {
        this.users = users;
        this.sessions = sessions;
        this.transaction = transaction;
    }

    @Transactional(readOnly = true)
    public ProfileResponse profile(Long userId) {
        return users.findById(userId).map(ProfileResponse::from)
                .orElseThrow(() -> ApiException.notFound("No encontrado."));
    }

    public ProfileResponse update(PlayerPrincipal player, JsonNode body) {
        try {
            return transaction.execute(status -> {
                User user = sessions.lock(player);
                ProfileChanges changes = ProfileChanges.from(body, user.getAvatarUrl(),
                        nickname -> users.existsByNicknameAndIdNot(nickname, user.getId()));
                user.updateProfile(changes.nickname(), changes.avatarUrl(), changes.firstName(), changes.lastName(),
                        changes.phone());
                users.flush();
                return ProfileResponse.from(user);
            });
        } catch (DataIntegrityViolationException exception) {
            if (DatabaseErrors.isUniqueViolation(exception)) {
                // Otro jugador tomó el apodo entre la validación y la escritura.
                throw new InvalidFieldsException(HttpStatus.CONFLICT,
                        Map.of("nickname", List.of(ProfileChanges.NICKNAME_TAKEN)));
            }
            throw exception;
        }
    }
}
