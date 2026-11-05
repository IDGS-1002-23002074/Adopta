package com.baite.adopta

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class RegisterTrackingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_tracking)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        intent.getStringExtra(Extras.PET_NAME)?.let { findViewById<TextView>(R.id.tvMascota).text = it }

        findViewById<MaterialButton>(R.id.btnGuardar).setOnClickListener {
            // TODO: guardar el seguimiento.
            Toast.makeText(this, "Seguimiento guardado", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
