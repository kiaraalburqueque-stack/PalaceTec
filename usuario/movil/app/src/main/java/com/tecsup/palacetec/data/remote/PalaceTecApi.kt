package com.tecsup.palacetec.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

interface PalaceTecApi {

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): RegisterResponse
}
