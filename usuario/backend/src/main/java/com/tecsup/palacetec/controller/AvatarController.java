package com.tecsup.palacetec.controller;

import com.tecsup.palacetec.dto.ProfileResponse;
import com.tecsup.palacetec.security.PlayerPrincipal;
import com.tecsup.palacetec.service.AvatarService;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
public class AvatarController {

    private final AvatarService avatars;

    public AvatarController(AvatarService avatars) {
        this.avatars = avatars;
    }

    /** multipart/form-data con el archivo en el campo avatar. */
    @PostMapping(path = "/api/v1/users/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileResponse upload(@AuthenticationPrincipal PlayerPrincipal player,
                                  MultipartHttpServletRequest request) throws IOException {
        return avatars.upload(player, request.getFile("avatar"));
    }
}
