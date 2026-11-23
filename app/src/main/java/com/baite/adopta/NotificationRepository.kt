package com.baite.adopta

import android.content.Context
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Notificaciones del usuario (RF043) desde la API: lista, no leídas y marcar como leída. */
object NotificationRepository {

    /** Total de no leídas según el servidor (alimenta el badge de la barra inferior). */
    var unread: Int = 0
        private set

    /**
     * Descarga las notificaciones y actualiza [SampleData.notificaciones].
     * Si se pasa [notifyContext], las no leídas que aparecieron desde la última descarga
     * se muestran también como notificación del sistema.
     */
    fun load(notifyContext: Context? = null, onDone: (error: String?) -> Unit = {}) {
        Api.call("GET", "/api/notificaciones?limit=50",
            onError = { onDone(it.message) }
        ) { r ->
            val list = parseList(r)
            SampleData.notificaciones = list
            unread = r.optInt("no_leidas", list.count { !it.leida })
            dispatchNew(notifyContext, list)
            onDone(null)
        }
    }

    fun parseList(r: JSONObject): List<AppNotification> {
        val arr = r.optJSONArray("items") ?: return emptyList()
        val list = mutableListOf<AppNotification>()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list += parse(it) }
        return list
    }

    /**
     * Publica como aviso del sistema las no leídas con id mayor al último visto, respetando
     * los interruptores de Ajustes. El "último visto" se guarda en disco, así la app y el
     * Worker en segundo plano nunca avisan dos veces lo mismo. Con [context] null solo avanza
     * el punto de partida (lo que ya viste dentro de la app no se vuelve a avisar).
     */
    fun dispatchNew(context: Context?, list: List<AppNotification>) {
        val last = NotificationPrefs.lastNotifiedId
        val maxId = list.maxOfOrNull { it.id } ?: 0
        // last < 0: primera descarga tras iniciar sesión; solo fija el punto de partida.
        if (context != null && last >= 0 && NotificationPrefs.pushEnabled) {
            list.filter { !it.leida && it.id > last && NotificationPrefs.isCategoryEnabled(it.tipo) }
                .sortedBy { it.id }
                .forEach { NotificationHelper.show(context, it) }
        }
        if (maxId > last) NotificationPrefs.lastNotifiedId = maxId
    }

    /** Marca como leída: se refleja al instante en pantalla y luego en el servidor. */
    fun markRead(id: Int) {
        SampleData.notificaciones = SampleData.notificaciones.map {
            if (it.id == id) it.copy(leida = true) else it
        }
        unread = (unread - 1).coerceAtLeast(0)
        Api.call("POST", "/api/notificaciones/$id/leida", onError = {}) { r ->
            unread = r.optInt("total", unread)
        }
    }

    /** Al cerrar sesión se olvida todo lo del usuario anterior. */
    fun reset() {
        SampleData.notificaciones = emptyList()
        unread = 0
    }

    private fun parse(o: JSONObject) = AppNotification(
        id = o.getInt("id_notificacion"),
        titulo = o.str("titulo") ?: "Notificación",
        mensaje = o.str("mensaje") ?: "",
        tiempo = relative(o.str("fecha_envio")),
        tipo = categoria(o.str("tipo")),
        leida = o.optBoolean("leida", false),
        idSolicitud = if (o.isNull("id_solicitud")) null else o.optInt("id_solicitud")
    )

    /** El backend usa más tipos que los 3 filtros de la pantalla. */
    private fun categoria(tipo: String?): String = when (tipo) {
        "Seguimiento" -> "Seguimiento"
        "Comunidad" -> "Comunidad"
        else -> "Solicitudes" // Solicitudes, Documentos, Devoluciones...
    }

    private fun relative(fecha: String?): String {
        if (fecha == null) return ""
        val ms: Long = (try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(fecha.take(19))?.time
        } catch (e: Exception) {
            null
        }) ?: return fecha
        val min = (System.currentTimeMillis() - ms) / 60_000
        return when {
            min < 1 -> "Ahora"
            min < 60 -> "Hace $min min"
            min < 60 * 24 -> "Hace ${min / 60} h"
            min < 60 * 48 -> "Ayer"
            min < 60 * 24 * 7 -> "Hace ${min / (60 * 24)} días"
            else -> SimpleDateFormat("d MMM yyyy", Locale("es", "MX")).format(Date(ms))
        }
    }
}
