package com.baite.adopta

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class ChangePasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnGuardar).setOnClickListener {
            // TODO: validar y guardar la contraseña.
            Toast.makeText(this, "Contraseña actualizada", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
