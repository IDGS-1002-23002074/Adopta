package com.baite.adopta

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Error devuelto por la API (o de red, con status = 0). */
class ApiException(
    val status: Int,
    override val message: String,
    val details: Map<String, String> = emptyMap()
) : Exception(message)

/** Lee un campo de texto de JSON devolviendo null si falta, es null o está vacío. */
fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }

/**
 * Cliente mínimo de la API Adopta+ (Node/Express). Todas las respuestas son objetos JSON.
 * Sin librerías externas: HttpURLConnection + un pequeño pool de hilos.
 */
object Api {

    /**
     * Emulador de Android Studio: 10.0.2.2 es el localhost de tu PC.
     * En un teléfono físico usa la IP de tu PC en la red, p. ej. "http://192.168.1.50:3000".
     */
    const val BASE_URL = "http://10.0.2.2:3000"

    /** Se invoca (en el hilo principal) cuando el token ya no es válido. */
    var onSessionExpired: (() -> Unit)? = null

    private val io = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    /** Llamada asíncrona: onOk/onError se ejecutan en el hilo principal. */
    fun call(
        method: String,
        path: String,
        body: JSONObject? = null,
        onError: (ApiException) -> Unit = {},
        onOk: (JSONObject) -> Unit
    ) {
        io.execute {
            try {
                val r = requestSync(method, path, body)
                main.post { onOk(r) }
            } catch (e: ApiException) {
                main.post { onError(e) }
            }
        }
    }

    /** Llamada síncrona. Úsala SOLO fuera del hilo principal. */
    @Throws(ApiException::class)
    fun requestSync(
        method: String,
        path: String,
        body: JSONObject? = null,
        handleExpiry: Boolean = true
    ): JSONObject {
        val conn = URL(BASE_URL + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/json")
            Session.token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            return readResponse(conn, handleExpiry)
        } catch (e: IOException) {
            throw ApiException(0, "No se pudo conectar con el servidor. Revisa tu conexión.")
        } finally {
            conn.disconnect()
        }
    }

    /** Sube un archivo (multipart/form-data) en un campo. onOk/onError en el hilo principal. */
    fun upload(
        path: String,
        field: String,
        filename: String,
        mime: String,
        bytes: ByteArray,
        onError: (ApiException) -> Unit = {},
        onOk: (JSONObject) -> Unit
    ) {
        io.execute {
            try {
                val r = uploadSync(path, field, filename, mime, bytes)
                main.post { onOk(r) }
            } catch (e: ApiException) {
                main.post { onError(e) }
            }
        }
    }

    @Throws(ApiException::class)
    fun uploadSync(path: String, field: String, filename: String, mime: String, bytes: ByteArray): JSONObject {
        val boundary = "----AdoptaBoundary${System.currentTimeMillis()}"
        val head = ("--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"$field\"; filename=\"$filename\"\r\n" +
            "Content-Type: $mime\r\n\r\n").toByteArray(Charsets.UTF_8)
        val tail = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)

        val conn = URL(BASE_URL + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            Session.token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            conn.setFixedLengthStreamingMode(head.size + bytes.size + tail.size)
            conn.outputStream.use {
                it.write(head)
                it.write(bytes)
                it.write(tail)
            }
            return readResponse(conn, true)
        } catch (e: IOException) {
            throw ApiException(0, "No se pudo conectar con el servidor. Revisa tu conexión.")
        } finally {
            conn.disconnect()
        }
    }

    internal fun readResponse(conn: HttpURLConnection, handleExpiry: Boolean): JSONObject {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val json = try {
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } catch (e: Exception) {
            JSONObject()
        }
        if (code in 200..299) return json

        val details = mutableMapOf<String, String>()
        json.optJSONObject("detalles")?.let { d ->
            d.keys().forEach { k -> details[k] = d.optString(k) }
        }
        if (code == 401 && handleExpiry) expireSession()
        throw ApiException(code, json.str("error") ?: "Error del servidor ($code)", details)
    }

    private fun expireSession() {
        // Todo en el hilo principal y solo si aún había sesión: evita avisar varias veces
        // cuando fallan peticiones en paralelo.
        main.post {
            if (Session.token != null) {
                Session.clear()
                onSessionExpired?.invoke()
            }
        }
    }
}
