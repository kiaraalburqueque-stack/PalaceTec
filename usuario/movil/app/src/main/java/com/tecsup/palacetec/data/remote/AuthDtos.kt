package com.tecsup.palacetec.data.remote

import com.google.gson.annotations.SerializedName

data class LoginRequest(val email: String, val password: String)

data class LoginResponse(
    val id: Long,
    val email: String,
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Long,
)

data class RegisterRequest(
    val email: String,
    val password: String,
    val dni: String,
    @SerializedName("birth_date") val birthDate: String,
)

data class RegisterResponse(val id: Long, val email: String)
