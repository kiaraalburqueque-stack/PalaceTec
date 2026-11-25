package com.tecsup.palacetec.dto;

import com.tecsup.palacetec.entity.User;

public record MeResponse(Long id, String email, String role) {

    public static MeResponse from(User user) {
        return new MeResponse(user.getId(), user.getEmail(), user.isAdmin() ? "ADMIN" : "USER");
    }
}
