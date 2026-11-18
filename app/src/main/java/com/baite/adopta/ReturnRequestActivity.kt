package com.baite.adopta

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.baite.adopta.data.AdopcionDetalle
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.Motivo
import com.baite.adopta.data.NuevaDevolucion
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.api
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/** RF033 — Solicitud de devolución: motivos del catálogo + explicación. */
class ReturnRequestActivity : BaseActivity() {

    private var id = -1
    private var sending = false
    private var motivos = emptyList<Motivo>()
    private lateinit var group: ChipGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_return_request)
        id = intent.getIntExtra(Extras.ADOPTION_ID, -1)
        if (id < 0) { finish(); return }
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        group = findViewById(R.id.cgMotivos)
        findViewById<MaterialButton>(R.id.btnEnviar).setOnClickListener { confirm() }
        load()
    }

    private fun load() {
        findViewById<View>(R.id.progressTop).visibility = View.VISIBLE
        lifecycleScope.launch {
            val a = async { api { ApiClient.api.adopcion(id) } }
            val c = async { api { ApiClient.api.catalogos() } }
            val ra = a.await(); val rc = c.await()
            findViewById<View>(R.id.progressTop).visibility = View.INVISIBLE
            when {
                ra is Outcome.Fail -> findViewById<View>(android.R.id.content).showFail(ra) { load() }
                rc is Outcome.Fail -> findViewById<View>(android.R.id.content).showFail(rc) { load() }
                ra is Outcome.Ok && rc is Outcome.Ok -> {
                    header(ra.value)
                    motivos = rc.value.motivosDevolucion
                    group.removeAllViews()
                    motivos.forEach { m ->
                        group.addView(Chip(this@ReturnRequestActivity).apply {
                            tag = m.idMotivo
                            text = m.nombre
                            isCheckable = true
                            isCheckedIconVisible = false
                            chipBackgroundColor = ContextCompat.getColorStateList(context, R.color.chip_bg)
                            setTextColor(ContextCompat.getColorStateList(context, R.color.chip_text))
                            chipStrokeWidth = dp(1).toFloat()
                            chipStrokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.adopta_line))
                            setOnCheckedChangeListener { _, _ -> findViewById<View>(R.id.tvErrMotivos).visible(false) }
                        })
                    }
                }
            }
        }
    }

    private fun header(a: AdopcionDetalle) {
        findViewById<ImageView>(R.id.ivFoto).loadPhoto(a.foto, a.idMascota)
        findViewById<TextView>(R.id.tvNombre).text = a.nombre
        findViewById<TextView>(R.id.tvSub).text = "${a.organizacion} · adoptada el ${com.baite.adopta.data.Format.date(a.fechaAdopcion)}"
    }

    private fun confirm() {
        val ids = (0 until group.childCount).map { group.getChildAt(it) as Chip }.filter { it.isChecked }.map { it.tag as Int }
        val til = findViewById<TextInputLayout>(R.id.tilExplicacion)
        val texto = findViewById<TextInputEditText>(R.id.etExplicacion).text?.toString().orEmpty().trim()
        findViewById<View>(R.id.tvErrMotivos).apply { visible(ids.isEmpty()); (this as TextView).text = "Selecciona al menos un motivo" }
        til.error = if (texto.length < 10) "Cuéntanos un poco más (mínimo 10 caracteres)" else null
        if (ids.isEmpty() || til.error != null || sending) return

        MaterialAlertDialogBuilder(this)
            .setTitle("¿Enviar solicitud de devolución?")
            .setMessage("La organización la revisará y se pondrá en contacto contigo. Mientras tanto, la mascota quedará marcada como \"en proceso de devolución\".")
            .setPositiveButton("Enviar") { _, _ -> send(ids, texto) }
            .setNegativeButton("Volver", null)
            .show()
    }

    private fun send(ids: List<Int>, texto: String) {
        sending = true
        val btn = findViewById<MaterialButton>(R.id.btnEnviar)
        btn.isEnabled = false
        findViewById<View>(R.id.progressTop).visibility = View.VISIBLE
        lifecycleScope.launch {
            when (val r = api { ApiClient.api.crearDevolucion(NuevaDevolucion(id, ids, texto)) }) {
                is Outcome.Ok -> {
                    MaterialAlertDialogBuilder(this@ReturnRequestActivity)
                        .setTitle("Solicitud enviada")
                        .setMessage("${r.value.organizacion} revisará tu solicitud. Puedes ver su estado en Mi hogar > Devoluciones.")
                        .setCancelable(false)
                        .setPositiveButton("Entendido") { _, _ -> finish() }
                        .show()
                }
                is Outcome.Fail -> {
                    findViewById<TextInputLayout>(R.id.tilExplicacion).error = r.fields["explicacion"]
                    findViewById<View>(android.R.id.content).showFail(r)
                    btn.isEnabled = true
                }
            }
            sending = false
            findViewById<View>(R.id.progressTop).visibility = View.INVISIBLE
        }
    }
}
