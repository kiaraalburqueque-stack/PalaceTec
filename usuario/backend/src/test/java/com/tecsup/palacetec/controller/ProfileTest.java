package com.tecsup.palacetec.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tecsup.palacetec.ApiTest;
import org.junit.jupiter.api.Test;

class ProfileTest extends ApiTest {

    @Test
    void meReturnsOnlyTheOwnProfile() throws Exception {
        newPlayer();
        Player player = newPlayer();
        mvc.perform(get("/api/v1/users/me").with(player.bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(player.id()))
                .andExpect(jsonPath("$.email").value(player.email()))
                .andExpect(jsonPath("$.dni").value(player.dni()))
                .andExpect(jsonPath("$.nickname").value(nullValue()))
                .andExpect(jsonPath("$.avatar_url").value("/avatars/avatar-1.svg"))
                .andExpect(jsonPath("$.first_name").value(""))
                .andExpect(jsonPath("$.last_name").value(""))
                .andExpect(jsonPath("$.phone").value(""))
                .andExpect(jsonPath("$.length()").value(8));
        mvc.perform(post("/api/v1/users/me").with(player.bearer())).andExpect(status().isMethodNotAllowed());
    }
}
