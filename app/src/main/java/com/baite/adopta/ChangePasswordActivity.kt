package com.baite.adopta

import android.os.Bundle
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.api
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/** Cambio de contraseña con la sesión iniciada. */
class ChangePasswordActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val tilActual = findViewById<TextInputLayout>(R.id.tilActual)
        val tilNueva = findViewById<TextInputLayout>(R.id.tilNueva)
        val tilConfirmar = findViewById<TextInputLayout>(R.id.tilConfirmar)
        val etActual = findViewById<TextInputEditText>(R.id.etActual)
        val etNueva = findViewById<TextInputEditText>(R.id.etNueva)
        val etConfirmar = findViewById<TextInputEditText>(R.id.etConfirmar)
        val btn = findViewById<MaterialButton>(R.id.btnGuardar)
        listOf(etActual to tilActual, etNueva to tilNueva, etConfirmar to tilConfirmar).forEach { (e, t) -> e.doAfterTextChanged { t.error = null } }

        btn.setOnClickListener {
            val actual = etActual.text?.toString().orEmpty()
            val nueva = etNueva.text?.toString().orEmpty()
            tilActual.error = if (actual.isEmpty()) "Escribe tu contraseña actual" else null
            tilNueva.error = passwordError(nueva) ?: if (nueva == actual) "Debe ser distinta a la actual" else null
            tilConfirmar.error = if (nueva != etConfirmar.text?.toString().orEmpty()) "Las contraseñas no coinciden" else null
            if (listOf(tilActual, tilNueva, tilConfirmar).any { it.error != null }) return@setOnClickListener

            btn.isEnabled = false
            lifecycleScope.launch {
                when (val r = api { ApiClient.api.changePassword(mapOf("contrasena_actual" to actual, "nueva_contrasena" to nueva)) }) {
                    is Outcome.Ok -> {
                        android.widget.Toast.makeText(this@ChangePasswordActivity, "Contraseña actualizada", android.widget.Toast.LENGTH_SHORT).show()
                        finish()
                        return@launch
                    }
                    is Outcome.Fail -> {
                        r.applyTo(mapOf("contrasena_actual" to tilActual, "nueva_contrasena" to tilNueva))
                        btn.rootView.showFail(r)
                    }
                }
                btn.isEnabled = true
            }
        }
    }
}
