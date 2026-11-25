package com.tecsup.palacetec.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

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

    @GET("api/v1/rooms")
    suspend fun rooms(): List<RouletteRoom>

    @GET("api/v1/rooms/{id}/state")
    suspend fun roomState(@Path("id") roomId: Long): RoomState

    @POST("api/v1/rooms/{id}/bets")
    suspend fun placeBet(@Path("id") roomId: Long, @Body request: BetRequest): BetResponse

    @GET("api/v1/users/bets-history")
    suspend fun betsHistory(@Query("page") page: Int, @Query("page_size") pageSize: Int): HistoryPage
}
