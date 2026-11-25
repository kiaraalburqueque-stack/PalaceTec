package com.tecsup.palacetec.data.remote

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
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

    @GET("api/v1/users/me")
    suspend fun profile(): Profile

    @PUT("api/v1/users/me")
    suspend fun updateProfile(@Body request: ProfileUpdate): Profile

    @Multipart
    @POST("api/v1/users/avatar")
    suspend fun uploadAvatar(@Part avatar: MultipartBody.Part): Profile

    @GET("api/v1/responsible-gaming/session-summary")
    suspend fun sessionSummary(): SessionSummary

    @GET("api/v1/responsible-gaming/risk-alerts")
    suspend fun riskAlerts(): RiskAlerts

    @POST("api/v1/responsible-gaming/pause")
    suspend fun pause(@Body request: PauseRequest): PauseResponse

    @POST("api/v1/responsible-gaming/self-exclude")
    suspend fun selfExclude(@Body request: SelfExclusionRequest): SelfExclusionResponse

    @GET("api/v1/notifications")
    suspend fun notifications(): NotificationList

    @POST("api/v1/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: Long): UnreadCount

    @POST("api/v1/notifications/read-all")
    suspend fun markAllNotificationsRead(): UnreadCount
}
