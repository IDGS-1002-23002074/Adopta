package com.baite.adopta

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.Session
import com.baite.adopta.data.api
import java.util.concurrent.TimeUnit

class NotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Inicializa sesión/cliente por si el proceso se levantó en frío desde cero
        Session.init(applicationContext)
        if (Session.token.isNullOrBlank()) return Result.success()

        return when (val r = api { ApiClient.api.notificaciones(null) }) {
            is Outcome.Ok -> {
                NotificationHelper.notifyNewFromServer(applicationContext, r.value.items)
                Result.success()
            }
            is Outcome.Fail -> Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "adopta_notif_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}