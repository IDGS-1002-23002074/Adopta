package com.baite.adopta

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/** Pantalla de inicio de sesión. */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)

        findViewById<MaterialButton>(R.id.btnLogin).setOnClickListener {
            if (etEmail.text.isNullOrBlank() || etPassword.text.isNullOrBlank()) {
                Toast.makeText(this, "Ingresa tu correo y contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // TODO: validar credenciales contra el backend antes de navegar.
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
        findViewById<TextView>(R.id.tvForgot).setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
        findViewById<TextView>(R.id.tvRegister).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}
