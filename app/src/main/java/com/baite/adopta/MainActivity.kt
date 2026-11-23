package com.baite.adopta

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.Session
import com.baite.adopta.data.api
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/** RF001 — Inicio de sesión. Identifica el rol y solo deja entrar a adoptantes. */
class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Session.isLoggedIn) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_main)
        applyInsetsOff()

        val tilEmail = findViewById<TextInputLayout>(R.id.tilEmail)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btn = findViewById<MaterialButton>(R.id.btnLogin)

        etEmail.doAfterTextChanged { tilEmail.error = null }
        etPassword.doAfterTextChanged { tilPassword.error = null }
        intent.getStringExtra(Extras.MESSAGE)?.let { findViewById<TextView>(R.id.tvForgot).rootView.snack(it) }

        btn.setOnClickListener {
            val email = etEmail.text?.toString().orEmpty().trim()
            val pass = etPassword.text?.toString().orEmpty()
            tilEmail.error = when {
                email.isEmpty() -> "Ingresa tu correo"
                !isEmail(email) -> "Revisa el formato del correo"
                else -> null
            }
            tilPassword.error = if (pass.isEmpty()) "Ingresa tu contraseña" else null
            if (tilEmail.error != null || tilPassword.error != null) return@setOnClickListener

            btn.isEnabled = false
            btn.text = "Entrando…"
            lifecycleScope.launch {
                when (val r = api { ApiClient.api.login(mapOf("correo" to email, "contrasena" to pass)) }) {
                    is Outcome.Ok -> {
                        if (r.value.usuario.rol != "Adoptante") {
                            btn.rootView.snack("Esta app es para adoptantes. Las organizaciones y administradores usan el portal web.")
                        } else {
                            Session.save(r.value.token, r.value.usuario)
                            startActivity(Intent(this@MainActivity, HomeActivity::class.java))
                            finish()
                            return@launch
                        }
                    }
                    is Outcome.Fail -> {
                        if (r.status == 401) tilPassword.error = r.message else btn.rootView.showFail(r) { btn.performClick() }
                    }
                }
                btn.isEnabled = true
                btn.text = "Iniciar sesión"
            }
        }
        findViewById<TextView>(R.id.tvForgot).setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java).putExtra(Extras.EMAIL, etEmail.text?.toString()?.trim()))
        }
        findViewById<TextView>(R.id.tvRegister).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

    }

    private fun applyInsetsOff() {
        // La cabecera con foto debe llegar hasta arriba: sin relleno superior.
        findViewById<android.view.ViewGroup>(android.R.id.content).setPadding(0, 0, 0, 0)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val b = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, 0, b.right, b.bottom)
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }
    }

}
