package com.baite.adopta

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
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

/** RF003 (paso 1) — Pide el correo y envía el código de verificación. */
class ForgotPasswordActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<TextView>(R.id.tvVolver).setOnClickListener { finish() }

        val til = findViewById<TextInputLayout>(R.id.tilEmail)
        val et = findViewById<TextInputEditText>(R.id.etEmail)
        val btn = findViewById<MaterialButton>(R.id.btnEnviar)
        et.setText(intent.getStringExtra(Extras.EMAIL).orEmpty())
        et.doAfterTextChanged { til.error = null }

        btn.setOnClickListener {
            val email = et.text?.toString().orEmpty().trim()
            if (!isEmail(email)) {
                til.error = "Escribe un correo electrónico válido"
                return@setOnClickListener
            }
            btn.isEnabled = false
            lifecycleScope.launch {
                when (val r = api { ApiClient.api.forgot(mapOf("correo" to email)) }) {
                    is Outcome.Ok -> startActivity(
                        Intent(this@ForgotPasswordActivity, ResetPasswordActivity::class.java)
                           .putExtra(Extras.EMAIL, email)
                    )
                    is Outcome.Fail -> btn.rootView.showFail(r) { btn.performClick() }
                }
                btn.isEnabled = true
            }
        }
    }
}
