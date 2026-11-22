package com.baite.adopta

import android.content.Intent
import android.widget.Toast
import android.os.Bundle
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.Session
import com.baite.adopta.data.api
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/** RF002 — Registro de adoptante. */
class RegisterActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        findViewById<TextView>(R.id.tvLogin).setOnClickListener { finish() }
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        fun et(id: Int) = findViewById<TextInputEditText>(id)
        fun til(id: Int) = findViewById<TextInputLayout>(id)
        val fields = mapOf(
            "nombre" to (et(R.id.etNombre) to til(R.id.tilNombre)),
            "apellido_paterno" to (et(R.id.etPaterno) to til(R.id.tilPaterno)),
            "apellido_materno" to (et(R.id.etMaterno) to til(R.id.tilMaterno)),
            "telefono" to (et(R.id.etTelefono) to til(R.id.tilTelefono)),
            "correo" to (et(R.id.etEmail) to til(R.id.tilEmail)),
            "contrasena" to (et(R.id.etPassword) to til(R.id.tilPassword)),
            "confirmar" to (et(R.id.etPassword2) to til(R.id.tilPassword2))
        )
        fields.values.forEach { (e, t) -> e.doAfterTextChanged { t.error = null } }
        val cb = findViewById<MaterialCheckBox>(R.id.cbTerminos)
        val btn = findViewById<MaterialButton>(R.id.btnRegister)

        btn.setOnClickListener {
            fun v(k: String) = fields.getValue(k).first.text?.toString().orEmpty().trim()
            fun err(k: String, m: String?) { fields.getValue(k).second.error = m }
            val pass = fields.getValue("contrasena").first.text?.toString().orEmpty()
            err("nombre", if (v("nombre").length < 2) "Escribe tu nombre" else null)
            err("apellido_paterno", if (v("apellido_paterno").isEmpty()) "Escribe tu apellido paterno" else null)
            err("telefono", if (v("telefono").isNotEmpty() && !Regex("^[0-9+\\s()-]{7,20}$").matches(v("telefono"))) "Teléfono no válido" else null)
            err("correo", if (!isEmail(v("correo"))) "Correo electrónico no válido" else null)
            err("contrasena", passwordError(pass))
            err("confirmar", if (pass != fields.getValue("confirmar").first.text?.toString().orEmpty()) "Las contraseñas no coinciden" else null)
            if (fields.values.any { it.second.error != null }) return@setOnClickListener
            if (!cb.isChecked) {
                btn.rootView.snack("Acepta los términos y el aviso de privacidad para continuar")
                return@setOnClickListener
            }

            btn.isEnabled = false
            btn.text = "Creando cuenta…"
            lifecycleScope.launch {
                val body = mapOf(
                    "nombre" to v("nombre"), "apellido_paterno" to v("apellido_paterno"),
                    "apellido_materno" to v("apellido_materno").ifEmpty { null },
                    "telefono" to v("telefono").ifEmpty { null },
                    "correo" to v("correo"), "contrasena" to pass
                )
                when (val r = api { ApiClient.api.register(body) }) {
                    is Outcome.Ok -> {
                        Toast.makeText(
                          this@RegisterActivity,
                          r.value.mensaje,
                          Toast.LENGTH_LONG
                       ).show()
                       val intent = Intent(
                          this@RegisterActivity,
                          VerifyEmailActivity::class.java
                       ).apply {
                           putExtra("correo", v("correo"))
                           putExtra("correo_enmascarado", r.value.correoEnmascarado)
                       }
                       startActivity(intent)
                       finish()
                    }
                    is Outcome.Fail -> {
                        r.applyTo(fields.mapValues { it.value.second })
                        btn.rootView.showFail(r) { btn.performClick() }
                    }
                }
                btn.isEnabled = true
                btn.text = "Crear cuenta"
            }
        }
    }
}
