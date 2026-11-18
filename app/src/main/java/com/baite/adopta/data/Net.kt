package com.baite.adopta.data

import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import retrofit2.HttpException
import java.io.IOException

/** Resultado de una llamada a la API: nunca lanza excepciones a la interfaz (RNF009, RNF014). */
sealed class Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>()
    data class Fail(
        val message: String,
        val status: Int = 0,
        /** Errores por campo que devuelve el servidor, p. ej. {"correo": "Ya está registrado"}. */
        val fields: Map<String, String> = emptyMap(),
        /** true cuando no hubo conexión: tiene sentido ofrecer "Reintentar". */
        val offline: Boolean = false
    ) : Outcome<Nothing>()
}

/** Eventos globales de la app. */
object AppEvents {
    /** Se emite cuando el servidor responde 401 con una sesión iniciada (RNF016). */
    val sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}

suspend fun <T> api(block: suspend () -> T): Outcome<T> {
    return try {
        Outcome.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        val body = try { e.response()?.errorBody()?.string() } catch (x: Exception) { null }
        var message = "Ocurrió un error (${e.code()}). Inténtalo de nuevo."
        val fields = mutableMapOf<String, String>()
        if (!body.isNullOrBlank()) {
            try {
                val json = JsonParser.parseString(body).asJsonObject
                json.get("error")?.takeIf { !it.isJsonNull }?.asString?.let { message = it }
                json.get("detalles")?.takeIf { it.isJsonObject }?.asJsonObject?.entrySet()?.forEach { (k, v) ->
                    if (v.isJsonPrimitive) fields[k] = v.asString
                }
            } catch (x: Exception) { /* cuerpo no JSON */ }
        }
        if (e.code() == 401 && Session.isLoggedIn) {
            Session.clear()
            AppEvents.sessionExpired.tryEmit(Unit)
        }
        Outcome.Fail(message, e.code(), fields)
    } catch (e: IOException) {
        Outcome.Fail("No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo.", offline = true)
    } catch (e: Exception) {
        Outcome.Fail("Algo salió mal. Inténtalo de nuevo.")
    }
}
