package com.baite.adopta

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class ForgotPasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<TextView>(R.id.tvVolver).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnEnviar).setOnClickListener {
            // TODO: solicitar el enlace de recuperación al backend.
            Toast.makeText(this, "Te enviamos un enlace de recuperación", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
