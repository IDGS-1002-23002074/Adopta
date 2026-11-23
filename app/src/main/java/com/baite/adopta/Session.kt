package com.baite.adopta

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/** Sesión del adoptante guardada en el dispositivo (token JWT + datos básicos). */
object Session {

    private const val FILE = "adopta_session"
    private const val K_TOKEN = "token"
    private const val K_ID = "id_usuario"
    private const val K_NOMBRE = "nombre"
    private const val K_APELLIDO = "apellido"
    private const val K_CORREO = "correo"
    private const val K_FOTO = "foto"

    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        if (prefs == null) {
            appContext = context.applicationContext
            prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
    }

    val token: String? get() = prefs?.getString(K_TOKEN, null)
    val isLoggedIn: Boolean get() = !token.isNullOrBlank()
    val userId: Int get() = prefs?.getInt(K_ID, -1) ?: -1
    val nombre: String get() = prefs?.getString(K_NOMBRE, "") ?: ""
    val apellido: String get() = prefs?.getString(K_APELLIDO, "") ?: ""
    val correo: String get() = prefs?.getString(K_CORREO, "") ?: ""
    val foto: String? get() = prefs?.getString(K_FOTO, null)?.takeIf { it.isNotBlank() }

    fun save(token: String, usuario: JSONObject) {
        prefs?.edit()
            ?.putString(K_TOKEN, token)
            ?.putInt(K_ID, usuario.optInt("id_usuario", -1))
            ?.putString(K_NOMBRE, usuario.str("nombre") ?: "")
            ?.putString(K_APELLIDO, usuario.str("apellido_paterno") ?: "")
            ?.putString(K_CORREO, usuario.str("correo") ?: "")
            ?.apply()
    }

    /** Actualiza nombre, apellido y foto con lo que devuelve GET/PUT /api/me. */
    fun updateProfile(usuario: JSONObject) {
        val e = prefs?.edit() ?: return
        usuario.str("nombre")?.let { e.putString(K_NOMBRE, it) }
        usuario.str("apellido_paterno")?.let { e.putString(K_APELLIDO, it) }
        if (usuario.has("foto_perfil")) e.putString(K_FOTO, usuario.str("foto_perfil") ?: "")
        e.apply()
    }

    fun setFoto(url: String?) {
        prefs?.edit()?.putString(K_FOTO, url ?: "")?.apply()
    }

    fun clear() {
        NotificationRepository.reset()
        NotificationPrefs.resetCursor()
        appContext?.let { NotificationWorker.cancel(it) }
        prefs?.edit()?.clear()?.apply()
    }
}
