package com.baite.adopta

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.rowEditar).setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
        findViewById<View>(R.id.rowPassword).setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnCerrarSesion).setOnClickListener { logout() }
        findViewById<TextView>(R.id.tvEliminar).setOnClickListener {
            // TODO: confirmar y eliminar la cuenta.
            Toast.makeText(this, "Función disponible próximamente", Toast.LENGTH_SHORT).show()
        }
    }

    private fun logout() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
    }
}
