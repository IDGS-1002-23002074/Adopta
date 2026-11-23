package com.baite.adopta

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Revisa el servidor cada ~15 min (mínimo que permite Android) aunque la app esté cerrada
 * y publica los avisos nuevos. Es consulta periódica (polling), no push en tiempo real:
 * para eso haría falta Firebase Cloud Messaging en el backend.
 */
class NotificationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        if (!Session.isLoggedIn || !NotificationPrefs.pushEnabled) return Result.success()
        return try {
            val r = Api.requestSync("GET", "/api/notificaciones?limit=30", handleExpiry = false)
            NotificationRepository.dispatchNew(applicationContext, NotificationRepository.parseList(r))
            Result.success()
        } catch (e: ApiException) {
            // 4xx (p. ej. token vencido): reintentar no sirve. Sin red / 5xx: WorkManager reintenta.
            if (e.status in 400..499) Result.success() else Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "adopta_notificaciones"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequest.Builder(NotificationWorker::class.java, 15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
