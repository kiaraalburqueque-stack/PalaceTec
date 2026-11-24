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
    val body = try {
        error.response()?.errorBody()?.string()
    } catch (_: IOException) {
        null
    }
    return parseErrorBody(error.code(), body, error)
}

/** Entiende {detail}, {code, detail}, {campo: [mensaje]}, {campo: mensaje} y [mensaje]. */
internal fun parseErrorBody(status: Int, body: String?, cause: Throwable? = null): ApiException {
    val json = try {
        body?.let { JsonParser.parseString(it) }
    } catch (_: JsonParseException) {
        null
    }
    val fallback = "Ocurrió un error ($status). Inténtalo nuevamente."
    if (json == null || !json.isJsonObject) {
        // Un cuerpo que no es JSON (por ejemplo HTML) no se muestra al jugador.
        val text = json?.takeIf { it.isJsonArray }?.firstText()
        return ApiException(status, null, text ?: fallback, cause = cause)
    }
    val entries = json.asJsonObject
    val detail = entries.get("detail")?.firstText()
    val fields = entries.entrySet()
        .filter { it.key !in setOf("detail", "code", "retry_after") }
        .mapNotNull { (name, value) -> value.firstText()?.let { name to it } }
        .toMap()
    return ApiException(
        status = status,
        code = entries.get("code")?.firstText(),
        message = detail ?: fields.values.firstOrNull() ?: fallback,
        fields = fields,
        cause = cause,
    )
}

/** El backend responde "texto" o ["texto"] según el caso. */
private fun JsonElement.firstText(): String? = when {
    isJsonPrimitive -> asString
    isJsonArray && !asJsonArray.isEmpty -> asJsonArray[0].firstText()
    else -> null
}
