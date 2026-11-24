package com.tecsup.palacetec.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface PalaceTecApi {

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): RegisterResponse

    @GET("api/v1/wallet/summary")
    suspend fun walletSummary(): WalletSummary

    @GET("api/v1/games")
    suspend fun games(): List<Game>

    @GET("api/v1/responsible-gaming/daily-limit")
    suspend fun dailyLimit(): DailyLimit

    @POST("api/v1/responsible-gaming/daily-limit")
    suspend fun setDailyLimit(@Body request: DailyLimitRequest): DailyLimit
}
