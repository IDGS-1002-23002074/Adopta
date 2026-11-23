package com.baite.adopta

import android.app.Application
import android.content.Intent
import android.widget.Toast

class AdoptaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Session.init(this)
        NotificationPrefs.init(this)
        NotificationHelper.ensureChannel(this)
        if (Session.isLoggedIn && NotificationPrefs.pushEnabled) NotificationWorker.schedule(this)

        // Si el backend responde 401 con sesión iniciada (token vencido), regresa al login.
        Api.onSessionExpired = {
            Toast.makeText(this, "Tu sesión expiró. Inicia sesión de nuevo", Toast.LENGTH_LONG).show()
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }
    }
}
