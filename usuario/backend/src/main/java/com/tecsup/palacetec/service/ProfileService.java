package com.tecsup.palacetec.service;

import com.tecsup.palacetec.dto.ProfileResponse;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consultas del jugador autenticado sobre sus propios datos. */
@Service
@Transactional(readOnly = true)
public class ProfileService {

    private final UserRepository users;

    public ProfileService(UserRepository users) {
        this.users = users;
    }

    public ProfileResponse profile(Long userId) {
        return users.findById(userId).map(ProfileResponse::from)
                .orElseThrow(() -> ApiException.notFound("No encontrado."));
    }
}
