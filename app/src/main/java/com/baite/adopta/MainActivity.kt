package com.baite.adopta

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONObject

/** Pantalla de inicio de sesión (conectada a POST /api/auth/login). */
class MainActivity : AppCompatActivity() {

    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var tvError: TextView
    private lateinit var btnLogin: MaterialButton
    private lateinit var progress: CircularProgressIndicator
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ya hay sesión guardada: entra directo.
        if (Session.isLoggedIn) {
            goHome()
            return
        }
        setContentView(R.layout.activity_main)

        tilEmail = findViewById(R.id.tilEmail)
        tilPassword = findViewById(R.id.tilPassword)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        tvError = findViewById(R.id.tvError)
        btnLogin = findViewById(R.id.btnLogin)
        progress = findViewById(R.id.progressLogin)

        btnLogin.setOnClickListener { attemptLogin() }
        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else false
        }
        findViewById<TextView>(R.id.tvForgot).setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
        findViewById<TextView>(R.id.tvRegister).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun attemptLogin() {
        if (loading) return
        val email = etEmail.text?.toString()?.trim().orEmpty()
        val pass = etPassword.text?.toString().orEmpty()

        tilEmail.error = null
        tilPassword.error = null
        tvError.visibility = View.GONE

        var valid = true
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = "Escribe un correo válido"
            valid = false
        }
        if (pass.isEmpty()) {
            tilPassword.error = "Escribe tu contraseña"
            valid = false
        }
        if (!valid) return

        setLoading(true)
        val body = JSONObject().put("correo", email).put("contrasena", pass)
        Api.call("POST", "/api/auth/login", body,
            onError = { e ->
                if (isDestroyed) return@call
                setLoading(false)
                showError(e.message)
            },
            onOk = { r ->
                if (isDestroyed) return@call
                val token = r.str("token")
                val usuario = r.optJSONObject("usuario")
                when {
                    token == null || usuario == null -> {
                        setLoading(false)
                        showError("Respuesta inesperada del servidor")
                    }
                    usuario.str("rol") != "Adoptante" -> {
                        setLoading(false)
                        showError("Esta app es solo para adoptantes. Usa el portal web de tu organización.")
                    }
                    else -> {
                        Session.save(token, usuario)
                        if (NotificationPrefs.pushEnabled) NotificationWorker.schedule(this)
                        goHome()
                    }
                }
            })
    }

    private fun setLoading(value: Boolean) {
        loading = value
        progress.visibility = if (value) View.VISIBLE else View.GONE
        btnLogin.text = if (value) "" else "Iniciar sesión"
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    private fun goHome() {
        startActivity(
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
