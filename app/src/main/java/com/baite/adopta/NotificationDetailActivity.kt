package com.baite.adopta

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

class NotificationDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_detail)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val n = SampleData.notificaciones.firstOrNull { it.id == intent.getIntExtra(Extras.NOTIF_ID, -1) }
        n?.let {
            findViewById<TextView>(R.id.tvTitulo).text = it.titulo
            findViewById<TextView>(R.id.tvTiempo).text = it.tiempo
            findViewById<TextView>(R.id.tvTipo).text = it.tipo
            findViewById<TextView>(R.id.tvMensaje).text = it.mensaje
        }

        findViewById<View>(R.id.btnAccion).setOnClickListener {
            val target = if (n?.tipo == "Seguimiento") TrackingDetailActivity::class.java
            else PetDetailActivity::class.java
            startActivity(Intent(this, target).putExtra(Extras.PET_ID, 1))
        }
        findViewById<View>(R.id.btnMarcarLeida).setOnClickListener {
            // TODO: persistir el estado "leída".
            Toast.makeText(this, "Marcada como leída", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
