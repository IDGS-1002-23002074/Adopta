package com.baite.adopta.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Sesión del usuario (RNF016). El token se guarda cifrado con Android Keystore.
 * Si el dispositivo no puede usar el cifrado, se usa un almacenamiento privado normal.
 */
object Session {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        val app = context.applicationContext
        prefs = try {
            val key = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                app, "adopta_session", key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            app.deleteSharedPreferences("adopta_session")
            app.getSharedPreferences("adopta_session_local", Context.MODE_PRIVATE)
        }
    }

    val token: String? get() = prefs.getString(KEY_TOKEN, null)
    val isLoggedIn: Boolean get() = !token.isNullOrBlank()

    val user: Usuario?
        get() = prefs.getString(KEY_USER, null)?.let {
            try { ApiClient.gson.fromJson(it, Usuario::class.java) } catch (e: Exception) { null }
        }

    fun save(token: String, user: Usuario) {
        prefs.edit().putString(KEY_TOKEN, token).putString(KEY_USER, ApiClient.gson.toJson(user)).apply()
    }

    fun updateUser(user: Usuario) {
        prefs.edit().putString(KEY_USER, ApiClient.gson.toJson(user)).apply()
    }

    /** Cierra la sesión (RF006): borra token y datos locales. */
    fun clear() {
        val server = serverUrl
        prefs.edit().clear().apply()
        if (server != null) prefs.edit().putString(KEY_SERVER, server).apply()
    }

    /** Servidor alternativo para desarrollo (celular físico). null = el de BuildConfig. */
    var serverUrl: String?
        get() = prefs.getString(KEY_SERVER, null)
        set(value) {
            prefs.edit().apply { if (value.isNullOrBlank()) remove(KEY_SERVER) else putString(KEY_SERVER, value) }.apply()
            ApiClient.reset()
        }

    // Opciones de notificaciones (RF005): qué categorías se muestran en Actividad
    fun notifEnabled(tipo: String): Boolean = prefs.getBoolean("notif_$tipo", true)
    fun setNotifEnabled(tipo: String, enabled: Boolean) = prefs.edit().putBoolean("notif_$tipo", enabled).apply()

    private const val KEY_TOKEN = "token"
    private const val KEY_USER = "user"
    private const val KEY_SERVER = "server"
}
