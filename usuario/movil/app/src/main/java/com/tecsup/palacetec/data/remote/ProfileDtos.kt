package com.tecsup.palacetec.data.remote

import com.google.gson.annotations.SerializedName

data class Profile(
    val id: Long,
    val email: String,
    val dni: String?,
    val nickname: String?,
    /** Uno de los predeterminados (/avatars/avatar-1.svg) o una foto subida (/media/avatars/…). */
    @SerializedName("avatar_url") val avatarUrl: String,
    @SerializedName("first_name") val firstName: String,
    @SerializedName("last_name") val lastName: String,
    val phone: String,
)

/** Los campos en null no se envían, y el backend conserva su valor. */
data class ProfileUpdate(
    val nickname: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
    val phone: String? = null,
)
