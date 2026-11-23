package com.baite.adopta

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.baite.adopta.data.Notificacion
import com.baite.adopta.data.Session

object NotificationHelper {

    private const val CHANNEL_ID = "adopta_avisos"
    private const val PREFS = "adopta_notif_local"
    private const val KEY_LAST_SHOWN_ID = "last_shown_id"
    private const val KEY_DISMISSED_IDS = "dismissed_ids"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Avisos de Adopta+",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de solicitudes, documentos y seguimiento"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    /** Revisa las notificaciones recibidas del servidor y muestra en el teléfono las nuevas no leídas. */
    fun notifyNewFromServer(context: Context, list: List<Notificacion>) {
        if (list.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastId = prefs.getInt(KEY_LAST_SHOWN_ID, 0)
        val maxId = list.maxOf { it.idNotificacion }

        // La primera vez sincronizamos el último ID para no inundar con historial viejo
        if (lastId == 0) {
            prefs.edit().putInt(KEY_LAST_SHOWN_ID, maxId).apply()
            return
        }

        val nuevas = list.filter {
            it.idNotificacion > lastId &&
                    !it.leida &&
                    !isDismissed(context, it.idNotificacion) &&
                    (it.tipo == null || Session.notifEnabled(it.tipo))
        }

        if (nuevas.isNotEmpty()) {
            createChannel(context)
            nuevas.sortedBy { it.idNotificacion }.forEach { showSystemNotification(context, it) }
        }
        if (maxId > lastId) {
            prefs.edit().putInt(KEY_LAST_SHOWN_ID, maxId).apply()
        }
    }

    fun showSystemNotification(context: Context, n: Notificacion) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = when {
            n.tipo == "Documentos" && n.idSolicitud != null && n.estadoSolicitud == "Documentacion pendiente" ->
                Intent(context, DocumentationActivity::class.java).putExtra(Extras.REQUEST_ID, n.idSolicitud)
            n.idSolicitud != null ->
                Intent(context, RequestDetailActivity::class.java).putExtra(Extras.REQUEST_ID, n.idSolicitud)
            else ->
                Intent(context, HomeActivity::class.java)
        }.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pi = PendingIntent.getActivity(
            context,
            n.idNotificacion,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(n.titulo)
            .setContentText(n.mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.mensaje))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        NotificationManagerCompat.from(context).notify(n.idNotificacion, notif)
    }

    fun dismiss(context: Context, idNotificacion: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_DISMISSED_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        set.add(idNotificacion.toString())
        prefs.edit().putStringSet(KEY_DISMISSED_IDS, set).apply()
    }

    fun isDismissed(context: Context, idNotificacion: Int): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_DISMISSED_IDS, emptySet())?.contains(idNotificacion.toString()) == true
    }
}