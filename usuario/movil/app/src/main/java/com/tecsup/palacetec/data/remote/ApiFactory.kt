package com.tecsup.palacetec.data.remote

import com.tecsup.palacetec.BuildConfig
import com.tecsup.palacetec.data.local.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiFactory {

    fun create(sessionStore: SessionStore, scope: CoroutineScope): PalaceTecApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(sessionStore, scope))
            .build()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PalaceTecApi::class.java)
    }
}

/** Agrega el Bearer y cierra la sesión local cuando el backend rechaza el token. */
private class AuthInterceptor(
    private val sessionStore: SessionStore,
    private val scope: CoroutineScope,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = sessionStore.token
        if (token == null || request.url.encodedPath.startsWith("/api/v1/auth/")) {
            return chain.proceed(request)
        }
        val response = chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $token").build(),
        )
        if (response.code == 401) {
            scope.launch { sessionStore.clearIfCurrent(token) }
        }
        return response
    }
}
