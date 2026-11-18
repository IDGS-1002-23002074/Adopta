package com.baite.adopta

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.baite.adopta.data.AdopcionDetalle
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.Format
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.api
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch

/** RF031 — Seguimiento de una mascota adoptada. */
class TrackingDetailActivity : BaseActivity() {

    private var id = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracking_detail)
        id = intent.getIntExtra(Extras.ADOPTION_ID, -1)
        if (id < 0) { finish(); return }
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.scroll).visibility = View.INVISIBLE
    }

    override fun onResume() {
        super.onResume()
        val bar = findViewById<LinearProgressIndicator>(R.id.progressTop)
        bar.visibility = View.VISIBLE
        lifecycleScope.launch {
            when (val r = api { ApiClient.api.adopcion(id) }) {
                is Outcome.Ok -> render(r.value)
                is Outcome.Fail -> findViewById<View>(android.R.id.content).showFail(r) { onResume() }
            }
            bar.visibility = View.INVISIBLE
        }
    }

    private fun render(a: AdopcionDetalle) {
        findViewById<View>(R.id.scroll).visibility = View.VISIBLE
        findViewById<ImageView>(R.id.ivFoto).loadPhoto(a.foto, a.idMascota)
        findViewById<TextView>(R.id.tvNombre).text = a.nombre
        findViewById<TextView>(R.id.tvSub).text = listOfNotNull(a.raza, a.especie).joinToString(" · ")
        findViewById<TextView>(R.id.tvOrg).text = a.organizacion
        findViewById<TextView>(R.id.tvEstado).applyStatus(a.estadoAdaptacion)
        findViewById<LinearProgressIndicator>(R.id.progAdaptacion).setProgressCompat(Format.adaptacionProgreso(a.estadoAdaptacion), true)
        findViewById<TextView>(R.id.tvAdaptacion).text = "Estado de adaptación: ${Format.estado(a.estadoAdaptacion)}"
        findViewById<TextView>(R.id.tvAdopcion).text = "Adoptada el ${Format.date(a.fechaAdopcion)} · ${a.totalReportes} reporte${if (a.totalReportes == 1) "" else "s"}"

        val dev = a.devolucion
        findViewById<View>(R.id.cardDevolucion).visible(dev != null)
        if (dev != null) {
            findViewById<TextView>(R.id.tvDevEstado).applyStatus(dev.estado)
            findViewById<TextView>(R.id.tvDevTexto).text = dev.observacionesOrganizacion?.takeIf { it.isNotBlank() }
                ?: "Enviaste tu solicitud el ${Format.date(dev.fechaSolicitud)}. La organización la está revisando."
        }

        val ultimo = a.reportes.firstOrNull()
        findViewById<TextView>(R.id.tvUltimoFecha).text = if (ultimo != null) Format.relative(ultimo.fechaReporte) else ""
        findViewById<TextView>(R.id.tvUltimoTexto).text = ultimo?.let { listOfNotNull(it.comportamiento, it.salud, it.observaciones).firstOrNull() }
            ?: "Aún no registras reportes. Cuéntanos cómo va la adaptación."

        findViewById<MaterialButton>(R.id.btnReporte).apply {
            visible(a.puedeReportar)
            setOnClickListener { startActivity(Intent(this@TrackingDetailActivity, RegisterTrackingActivity::class.java).putExtra(Extras.ADOPTION_ID, id)) }
        }
        findViewById<MaterialButton>(R.id.btnHistorial).setOnClickListener {
            startActivity(Intent(this, TrackingHistoryActivity::class.java).putExtra(Extras.ADOPTION_ID, id))
        }
        findViewById<MaterialButton>(R.id.btnDevolucion).apply {
            visible(a.puedeDevolver)
            setOnClickListener { startActivity(Intent(this@TrackingDetailActivity, ReturnRequestActivity::class.java).putExtra(Extras.ADOPTION_ID, id)) }
        }
    }
}
