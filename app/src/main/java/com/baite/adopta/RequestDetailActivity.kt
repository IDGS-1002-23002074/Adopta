package com.baite.adopta

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.Format
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.SolicitudDetalle
import com.baite.adopta.data.api
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch

/** RF024-RF026 — Estado, historial y acciones de una solicitud de adopción. */
class RequestDetailActivity : BaseActivity() {

    private var id = -1
    private var detalle: SolicitudDetalle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_detail)
        id = intent.getIntExtra(Extras.REQUEST_ID, -1)
        if (id < 0) { finish(); return }
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.scroll).visibility = View.INVISIBLE
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val bar = findViewById<LinearProgressIndicator>(R.id.progressTop)
        bar.visibility = View.VISIBLE
        lifecycleScope.launch {
            when (val r = api { ApiClient.api.solicitud(id) }) {
                is Outcome.Ok -> { detalle = r.value; render(r.value) }
                is Outcome.Fail -> findViewById<View>(android.R.id.content).showFail(r) { load() }
            }
            bar.visibility = View.INVISIBLE
        }
    }

    private fun render(d: SolicitudDetalle) {
        findViewById<View>(R.id.scroll).visibility = View.VISIBLE
        findViewById<ImageView>(R.id.ivMascota).loadPhoto(d.fotoMascota, d.idMascota)
        findViewById<TextView>(R.id.tvMascota).text = d.mascota
        findViewById<TextView>(R.id.tvOrg).text = d.organizacion
        findViewById<TextView>(R.id.tvEstado).applyStatus(d.estado)
        findViewById<TextView>(R.id.tvFechas).text = buildString {
            append("Enviada el ${Format.date(d.fechaSolicitud)}")
            if (!d.fechaResolucion.isNullOrBlank()) append(" · Resuelta el ${Format.date(d.fechaResolucion)}")
        }

        // Documentación: solo cuando la organización la está esperando
        val cardDocs = findViewById<View>(R.id.cardDocs)
        cardDocs.visible(d.puedeEditarDocumentos)
        if (d.puedeEditarDocumentos) {
            val faltan = mutableListOf<String>()
            if (d.pendientes.documentos > 0) faltan += "${d.pendientes.documentos} documento${if (d.pendientes.documentos == 1) "" else "s"}"
            if (d.pendientes.informacion) faltan += "la información del hogar"
            if (d.pendientes.referencias > 0) faltan += "${d.pendientes.referencias} referencia${if (d.pendientes.referencias == 1) "" else "s"}"
            val rechazados = d.requisitos.filter { it.estado == "Rechazado" }
            findViewById<TextView>(R.id.tvDocsTexto).text = buildString {
                append(if (faltan.isEmpty()) "Todo está listo para enviarse a revisión." else "Te falta: ${faltan.joinToString(", ")}.")
                if (!d.fechaLimiteDocumentos.isNullOrBlank()) append("\nFecha límite: ${Format.date(d.fechaLimiteDocumentos)}.")
                rechazados.forEach { append("\n• ${it.nombre}: ${it.motivoRechazo ?: "debe corregirse"}") }
            }
            findViewById<MaterialButton>(R.id.btnDocumentos).setOnClickListener {
                startActivity(Intent(this, DocumentationActivity::class.java).putExtra(Extras.REQUEST_ID, id))
            }
        }
        val obs = d.observacionesOrganizacion?.takeIf { it.isNotBlank() }
        findViewById<View>(R.id.cardObs).visible(obs != null)
        findViewById<TextView>(R.id.tvObservaciones).text = obs

        // Historial (línea de tiempo)
        val ll = findViewById<android.widget.LinearLayout>(R.id.llHistorial)
        ll.removeAllViews()
        d.historial.forEachIndexed { i, h ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_timeline, ll, false)
            val last = i == d.historial.lastIndex
            row.findViewById<TextView>(R.id.tvTitulo).text = Format.estado(h.estado)
            row.findViewById<TextView>(R.id.tvFecha).text = Format.date(h.fechaCambio)
            row.findViewById<TextView>(R.id.tvNota).apply { visible(!h.observacion.isNullOrBlank()); text = h.observacion }
            row.findViewById<View>(R.id.vLinea).visible(!last)
            row.findViewById<View>(R.id.vPunto).backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, if (last) R.color.adopta_teal_dark else R.color.adopta_teal)
            )
            ll.addView(row)
        }

        findViewById<TextView>(R.id.tvMotivo).text = d.motivoAdopcion ?: "—"
        findViewById<TextView>(R.id.tvExperiencia).text = d.experienciaPrevia?.takeIf { it.isNotBlank() } ?: "—"
        findViewById<TextView>(R.id.tvVivienda).text = d.condicionesVivienda ?: "—"

        findViewById<MaterialButton>(R.id.btnVerMascota).setOnClickListener {
            startActivity(Intent(this, PetDetailActivity::class.java).putExtra(Extras.PET_ID, d.idMascota))
        }
        findViewById<MaterialButton>(R.id.btnCancelar).apply {
            visible(d.puedeCancelar)
            setOnClickListener { confirmCancel(d) }
        }
    }

    private fun confirmCancel(d: SolicitudDetalle) {
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Cancelar la solicitud?")
            .setMessage("Dejarás de participar en la adopción de ${d.mascota}. Podrás volver a solicitarla más adelante.")
            .setPositiveButton("Sí, cancelar") { _, _ ->
                findViewById<LinearProgressIndicator>(R.id.progressTop).visibility = View.VISIBLE
                lifecycleScope.launch {
                    when (val r = api { ApiClient.api.cancelarSolicitud(id, mapOf("motivo" to "Cancelada por el adoptante")) }) {
                        is Outcome.Ok -> { detalle = r.value; render(r.value) }
                        is Outcome.Fail -> findViewById<View>(android.R.id.content).showFail(r)
                    }
                    findViewById<LinearProgressIndicator>(R.id.progressTop).visibility = View.INVISIBLE
                }
            }
            .setNegativeButton("Volver", null)
            .show()
    }
}
