package com.baite.adopta

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class EditProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnGuardar).setOnClickListener {
            // TODO: guardar los cambios del perfil.
            Toast.makeText(this, "Cambios guardados", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
