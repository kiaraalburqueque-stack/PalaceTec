package com.tecsup.palacetec.data.remote

import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * Error de la API listo para mostrarse.
 * status es null cuando no hubo respuesta del servidor; fields trae los errores por campo.
 */
class ApiException(
    val status: Int?,
    val code: String?,
    override val message: String,
    val fields: Map<String, String> = emptyMap(),
    cause: Throwable? = null,
) : Exception(message, cause)

/** Ejecuta la llamada en Dispatchers.IO y traduce los fallos a ApiException. */
suspend fun <T> apiCall(block: suspend () -> T): T = withContext(Dispatchers.IO) {
    try {
        block()
    } catch (error: HttpException) {
        throw parseError(error)
    } catch (error: JsonParseException) {
        throw ApiException(null, null, "El servidor envió una respuesta inesperada.", cause = error)
    } catch (error: IOException) {
        throw ApiException(
            null, null, "No se pudo conectar con el servidor. Revisa tu conexión e inténtalo otra vez.", cause = error,
        )
    }
}

private fun parseError(error: HttpException): ApiException {
    val json = try {
        error.response()?.errorBody()?.string()?.let { JsonParser.parseString(it) }
    } catch (_: JsonParseException) {
        null
    } catch (_: IOException) {
        null
    }
    val fallback = "Ocurrió un error (${error.code()}). Inténtalo nuevamente."
    if (json == null || !json.isJsonObject) {
        return ApiException(error.code(), null, json?.firstText() ?: fallback, cause = error)
    }
    val body = json.asJsonObject
    val detail = body.get("detail")?.firstText()
    val fields = body.entrySet()
        .filter { it.key !in setOf("detail", "code", "retry_after") }
        .mapNotNull { (name, value) -> value.firstText()?.let { name to it } }
        .toMap()
    return ApiException(
        status = error.code(),
        code = body.get("code")?.firstText(),
        message = detail ?: fields.values.firstOrNull() ?: fallback,
        fields = fields,
        cause = error,
    )
}

/** El backend responde "texto" o ["texto"] según el caso. */
private fun JsonElement.firstText(): String? = when {
    isJsonPrimitive -> asString
    isJsonArray && !asJsonArray.isEmpty -> asJsonArray[0].firstText()
    else -> null
}
