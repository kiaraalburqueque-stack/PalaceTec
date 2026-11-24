package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tecsup.palacetec.entity.User;

public record ProfileResponse(
        Long id,
        String email,
        String dni,
        String nickname,
        @JsonProperty("avatar_url") String avatarUrl,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        String phone) {

    public static ProfileResponse from(User user) {
        return new ProfileResponse(user.getId(), user.getEmail(), user.getDni(), user.getNickname(),
                user.getAvatarUrl(), user.getFirstName(), user.getLastName(), user.getPhone());
    }
}
