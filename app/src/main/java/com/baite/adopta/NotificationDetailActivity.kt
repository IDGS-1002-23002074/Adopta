package com.baite.adopta

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

class NotificationDetailActivity : AppCompatActivity() {

    private var current: AppNotification? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Si se abrió desde un aviso del sistema después de cerrar sesión, no hay nada que mostrar.
        if (!Session.isLoggedIn) {
            finish()
            return
        }
        setContentView(R.layout.activity_notification_detail)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val id = intent.getIntExtra(Extras.NOTIF_ID, -1)
        current = SampleData.notificaciones.firstOrNull { it.id == id }
        bind()
        if (current == null) {
            // Abierta desde la barra de estado con la app cerrada: la lista aún no está en memoria.
            NotificationRepository.load {
                if (isDestroyed) return@load
                current = SampleData.notificaciones.firstOrNull { it.id == id }
                bind()
            }
        }

        findViewById<View>(R.id.btnAccion).setOnClickListener {
            val target = if (current?.tipo == "Seguimiento") TrackingDetailActivity::class.java
            else PetDetailActivity::class.java
            startActivity(Intent(this, target).putExtra(Extras.PET_ID, 1))
        }
        findViewById<View>(R.id.btnMarcarLeida).setOnClickListener {
            current?.let { NotificationRepository.markRead(it.id) }
            Toast.makeText(this, "Marcada como leída", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun bind() {
        current?.let {
            findViewById<TextView>(R.id.tvTitulo).text = it.titulo
            findViewById<TextView>(R.id.tvTiempo).text = it.tiempo
            findViewById<TextView>(R.id.tvTipo).text = it.tipo
            findViewById<TextView>(R.id.tvMensaje).text = it.mensaje
        }
    }
}
