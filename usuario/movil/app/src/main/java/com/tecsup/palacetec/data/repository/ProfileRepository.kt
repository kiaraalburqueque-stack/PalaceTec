package com.tecsup.palacetec.data.repository

import com.tecsup.palacetec.data.local.AvatarSource
import com.tecsup.palacetec.data.remote.PalaceTecApi
import com.tecsup.palacetec.data.remote.Profile
import com.tecsup.palacetec.data.remote.ProfileUpdate
import com.tecsup.palacetec.data.remote.apiCall
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class ProfileRepository(
    private val api: PalaceTecApi,
    private val avatars: AvatarSource,
    private val baseUrl: String,
) {
    suspend fun get(): Profile = apiCall { api.profile() }

    suspend fun update(changes: ProfileUpdate): Profile = apiCall { api.updateProfile(changes) }

    suspend fun uploadAvatar(uri: String): Profile {
        val file = avatars.read(uri)
        val body = file.bytes.toRequestBody(file.mime.toMediaType())
        val name = "avatar." + file.mime.substringAfter('/')
        return apiCall { api.uploadAvatar(MultipartBody.Part.createFormData("avatar", name, body)) }
    }

    /** Solo las fotos subidas están en el servidor; los avatares predeterminados los dibuja la app. */
    fun photoUrl(avatarUrl: String): String? =
        if (avatarUrl.startsWith("/media/")) baseUrl.trimEnd('/') + avatarUrl else null
}
