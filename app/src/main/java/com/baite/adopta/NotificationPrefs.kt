package com.baite.adopta

import android.content.Context
import android.content.SharedPreferences

/** Preferencias de avisos del dispositivo y el último aviso ya notificado (evita duplicados). */
object NotificationPrefs {

    private const val FILE = "adopta_notifications"
    private const val K_PUSH = "push"
    private const val K_LAST_ID = "last_notified_id"
    private const val K_ASKED = "asked_permission"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
    }

    /** Interruptor general de "Notificaciones push" en Ajustes. */
    var pushEnabled: Boolean
        get() = prefs?.getBoolean(K_PUSH, true) ?: true
        set(v) { prefs?.edit()?.putBoolean(K_PUSH, v)?.apply() }

    /** Categoría: "Solicitudes", "Seguimiento" o "Comunidad". */
    fun isCategoryEnabled(categoria: String): Boolean =
        prefs?.getBoolean("cat_$categoria", true) ?: true

    fun setCategoryEnabled(categoria: String, value: Boolean) {
        prefs?.edit()?.putBoolean("cat_$categoria", value)?.apply()
    }

    /** Mayor id de notificación ya visto/avisado. -1 = aún no hay punto de partida. */
    var lastNotifiedId: Int
        get() = prefs?.getInt(K_LAST_ID, -1) ?: -1
        set(v) { prefs?.edit()?.putInt(K_LAST_ID, v)?.apply() }

    /** Para pedir el permiso de Android 13+ solo una vez al abrir la app. */
    var askedPermission: Boolean
        get() = prefs?.getBoolean(K_ASKED, false) ?: false
        set(v) { prefs?.edit()?.putBoolean(K_ASKED, v)?.apply() }

    /** Se llama al cerrar sesión: el siguiente usuario empieza sin historial de avisos. */
    fun resetCursor() {
        prefs?.edit()?.remove(K_LAST_ID)?.apply()
    }
}
