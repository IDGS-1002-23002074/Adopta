
package com.baite.adopta

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.baite.adopta.data.ApiClient
import kotlinx.coroutines.launch

class VerifyEmailActivity : AppCompatActivity() {

    private lateinit var etCodigo: EditText
    private lateinit var btnVerificar: Button
    private lateinit var btnReenviar: TextView
    private lateinit var tvCorreo: TextView
    private lateinit var tvContador: TextView

    private var correo = ""
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_email)

        correo = intent.getStringExtra("correo") ?: ""

        etCodigo = findViewById(R.id.etCodigoVerificacion)
        btnVerificar = findViewById(R.id.btnVerificarCorreo)
        btnReenviar = findViewById(R.id.btnReenviarCodigo)
        tvCorreo = findViewById(R.id.tvCorreoVerificacion)
        tvContador = findViewById(R.id.tvContadorReenvio)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
           .setNavigationOnClickListener {
              finish()
        }

        val correoMostrar = intent.getStringExtra("correo_enmascarado")
            ?.takeIf { it.isNotBlank() } ?: correo

        tvCorreo.text = "Enviamos un código a:\n$correoMostrar"

        iniciarContador()

        btnVerificar.setOnClickListener {
            verificarCodigo()
        }

        btnReenviar.setOnClickListener {
            reenviarCodigo()
        }
    }

    private fun verificarCodigo() {
        val codigo = etCodigo.text.toString().trim()

        if (!codigo.matches(Regex("^\\d{6}$"))) {
            etCodigo.error = "Ingresa los seis dígitos"
            return
        }

        btnVerificar.isEnabled = false

        lifecycleScope.launch {
            try {
                val respuesta = ApiClient.api.verifyEmail(
                    mapOf(
                        "correo" to correo,
                        "codigo" to codigo
                    )
                )

                Toast.makeText(
                    this@VerifyEmailActivity,
                    respuesta.mensaje,
                    Toast.LENGTH_LONG
                ).show()

                val intent = Intent(
                    this@VerifyEmailActivity,
                    MainActivity::class.java
                )

                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK

                startActivity(intent)
                finish()

            } catch (e: Exception) {
                Toast.makeText(
                    this@VerifyEmailActivity,
                    obtenerError(e),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                btnVerificar.isEnabled = true
            }
        }
    }

    private fun reenviarCodigo() {
        btnReenviar.isEnabled = false

        lifecycleScope.launch {
            try {
                val respuesta = ApiClient.api.resendVerification(
                    mapOf("correo" to correo)
                )

                Toast.makeText(
                    this@VerifyEmailActivity,
                    respuesta.mensaje,
                    Toast.LENGTH_LONG
                ).show()

                iniciarContador()

            } catch (e: Exception) {
                Toast.makeText(
                    this@VerifyEmailActivity,
                    obtenerError(e),
                    Toast.LENGTH_LONG
                ).show()

                btnReenviar.isEnabled = true
            }
        }
    }

    private fun iniciarContador() {
        timer?.cancel()
        btnReenviar.isEnabled = false

        timer = object : CountDownTimer(60_000, 1_000) {
            override fun onTick(millisUntilFinished: Long) {
                val segundos = millisUntilFinished / 1000
                tvContador.text = "Puedes solicitar otro código en ${segundos}s"
            }

            override fun onFinish() {
                tvContador.text = "¿No recibiste el código?"
                btnReenviar.isEnabled = true
            }
        }.start()
    }

    private fun obtenerError(e: Exception): String {
        val mensaje = e.message ?: "Ocurrió un error"

        return when {
            mensaje.contains("400") ->
                "El código es incorrecto o venció. Revisa tu correo."

            mensaje.contains("429") ->
                "Espera un momento antes de volver a intentarlo."

            mensaje.contains("503") ->
                "No fue posible enviar el correo. Intenta más tarde."

            else -> "No se pudo completar la operación: $mensaje"
        }
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
