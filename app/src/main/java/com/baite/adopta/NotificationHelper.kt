package com.baite.adopta

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat

/** Muestra avisos en la barra de estado del dispositivo. */
object NotificationHelper {

    const val CHANNEL_ID = "adopta_avisos"

    /** Se llama al iniciar la app (AdoptaApp) para que el canal exista antes del primer aviso. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID, "Avisos de Adopta+", NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Solicitudes, seguimiento y novedades de la comunidad" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    // areNotificationsEnabled() ya cubre el permiso POST_NOTIFICATIONS de Android 13+.
    @SuppressLint("MissingPermission")
    fun show(context: Context, n: AppNotification) {
        val ctx = context.applicationContext
        val manager = NotificationManagerCompat.from(ctx)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(ctx)

        // Al tocar el aviso: Inicio -> Detalle. Así "atrás" regresa a la app y no al launcher.
        val detalle = Intent(ctx, NotificationDetailActivity::class.java).putExtra(Extras.NOTIF_ID, n.id)
        val pending = TaskStackBuilder.create(ctx)
            .addNextIntent(Intent(ctx, HomeActivity::class.java))
            .addNextIntent(detalle)
            .getPendingIntent(n.id, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            ?: return

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications)
            .setColor(ContextCompat.getColor(ctx, R.color.adopta_teal_dark))
            .setContentTitle(n.titulo)
            .setContentText(n.mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.mensaje))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(n.id, notification)
    }
}
