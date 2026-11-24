package com.tecsup.palacetec.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiFactory {

    /** onRejected avisa que el backend ya no acepta ese token, con su mensaje. */
    fun create(
        baseUrl: String,
        token: () -> String?,
        onRejected: (token: String, message: String?) -> Unit,
    ): PalaceTecApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(token, onRejected))
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PalaceTecApi::class.java)
    }
}

/** Agrega el Bearer y detecta cuando la sesión dejó de valer. */
private class AuthInterceptor(
    private val token: () -> String?,
    private val onRejected: (token: String, message: String?) -> Unit,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = token()
        if (token == null || request.url.encodedPath.startsWith("/api/v1/auth/")) {
            return chain.proceed(request)
        }
        val response = chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $token").build(),
        )
        if (response.code == 401 || response.code == 403) {
            // peekBody no consume la respuesta: quien llamó todavía la lee completa.
            val error = parseErrorBody(response.code, response.peekBody(MAX_ERROR_BYTES).string())
            // 403: jugador excluido o autoexcluido durante la sesión.
            if (response.code == 401 || error.code == "access_not_allowed") onRejected(token, error.message)
        }
        return response
    }

    private companion object {
        const val MAX_ERROR_BYTES = 64L * 1024
    }
}
