package com.baite.adopta

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.Format
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.api
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/** RF003 (paso 2) — Código + nueva contraseña. "Volver a enviar" tras 25 s, como en el diseño. */
class ResetPasswordActivity : BaseActivity() {

    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val email = intent.getStringExtra(Extras.EMAIL).orEmpty()
        findViewById<TextView>(R.id.tvCorreo).text = Format.mask(email)

        val tilCodigo = findViewById<TextInputLayout>(R.id.tilCodigo)
        val tilNueva = findViewById<TextInputLayout>(R.id.tilNueva)
        val tilConfirmar = findViewById<TextInputLayout>(R.id.tilConfirmar)
        val etCodigo = findViewById<TextInputEditText>(R.id.etCodigo)
        val etNueva = findViewById<TextInputEditText>(R.id.etNueva)
        val etConfirmar = findViewById<TextInputEditText>(R.id.etConfirmar)
        val btn = findViewById<MaterialButton>(R.id.btnGuardar)
        val tvReenviar = findViewById<TextView>(R.id.tvReenviar)
        listOf(etCodigo to tilCodigo, etNueva to tilNueva, etConfirmar to tilConfirmar).forEach { (e, t) -> e.doAfterTextChanged { t.error = null } }


        fun startTimer() {
            tvReenviar.isEnabled = false
            tvReenviar.setTextColor(ContextCompat.getColor(this, R.color.adopta_gray))
            timer?.cancel()
            timer = object : CountDownTimer(25_000, 1_000) {
                override fun onTick(ms: Long) { tvReenviar.text = "Volver a enviar en ${ms / 1000 + 1} s" }
                override fun onFinish() {
                    tvReenviar.text = "Volver a enviar"
                    tvReenviar.isEnabled = true
                    tvReenviar.setTextColor(ContextCompat.getColor(this@ResetPasswordActivity, R.color.adopta_teal_dark))
                }
            }.start()
        }
        startTimer()

        tvReenviar.setOnClickListener {
            lifecycleScope.launch {
                when (val r = api { ApiClient.api.forgot(mapOf("correo" to email)) }) {
                    is Outcome.Ok -> {
                        tvReenviar.rootView.snack("Te enviamos un nuevo código")
                        startTimer()
                    }
                    is Outcome.Fail -> tvReenviar.rootView.showFail(r)
                }
            }
        }

        btn.setOnClickListener {
            val codigo = etCodigo.text?.toString().orEmpty().trim()
            val nueva = etNueva.text?.toString().orEmpty()
            tilCodigo.error = if (!Regex("^\\d{6}$").matches(codigo)) "El código tiene 6 dígitos" else null
            tilNueva.error = passwordError(nueva)
            tilConfirmar.error = if (nueva != etConfirmar.text?.toString().orEmpty()) "Las contraseñas no coinciden" else null
            if (listOf(tilCodigo, tilNueva, tilConfirmar).any { it.error != null }) return@setOnClickListener

            btn.isEnabled = false
            lifecycleScope.launch {
                when (val r = api { ApiClient.api.reset(mapOf("correo" to email, "codigo" to codigo, "nueva_contrasena" to nueva)) }) {
                    is Outcome.Ok -> {
                        startActivity(
                            Intent(this@ResetPasswordActivity, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                .putExtra(Extras.MESSAGE, "Contraseña actualizada. Ya puedes iniciar sesión.")
                        )
                        finish()
                        return@launch
                    }
                    is Outcome.Fail -> {
                        if (r.status == 400) tilCodigo.error = r.message else btn.rootView.showFail(r)
                        r.applyTo(mapOf("nueva_contrasena" to tilNueva))
                    }
                }
                btn.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
