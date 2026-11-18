package com.baite.adopta

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.CambioAdaptacion
import com.baite.adopta.data.Format
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.Reporte
import com.baite.adopta.data.api
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.launch

/** RF032 — Historial: tus reportes y los cambios de estado de adaptación que registró la organización. */
class TrackingHistoryActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracking_history)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        val rv = findViewById<RecyclerView>(R.id.rvHistorial)
        rv.layoutManager = LinearLayoutManager(this)
        val adapter = HistoryAdapter()
        rv.adapter = adapter
        load(adapter)
    }

    private fun load(adapter: HistoryAdapter) {
        val id = intent.getIntExtra(Extras.ADOPTION_ID, -1)
        val bar = findViewById<View>(R.id.progressTop)
        bar.visibility = View.VISIBLE
        lifecycleScope.launch {
            when (val r = api { ApiClient.api.adopcion(id) }) {
                is Outcome.Ok -> {
                    val all = (r.value.reportes + r.value.historialAdaptacion).sortedByDescending {
                        Format.parse(if (it is Reporte) it.fechaReporte else (it as CambioAdaptacion).fechaCambio)?.time ?: 0L
                    }
                    adapter.submit(all)
                    findViewById<View>(R.id.tvVacio).visible(all.isEmpty())
                }
                is Outcome.Fail -> findViewById<View>(android.R.id.content).showFail(r) { load(adapter) }
            }
            bar.visibility = View.INVISIBLE
        }
    }
}

private class HistoryAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var items: List<Any> = emptyList()
    class VH(v: View) : RecyclerView.ViewHolder(v)

    fun submit(list: List<Any>) { items = list; notifyDataSetChanged() }
    override fun getItemViewType(position: Int) = if (items[position] is Reporte) 0 else 1
    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(if (viewType == 0) R.layout.item_reporte else R.layout.item_cambio, parent, false))

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
        val v = h.itemView
        when (val it = items[position]) {
            is Reporte -> {
                v.findViewById<TextView>(R.id.tvFecha).text = Format.date(it.fechaReporte)
                v.findViewById<TextView>(R.id.tvTexto).text = listOf(
                    "Comportamiento" to it.comportamiento, "Salud" to it.salud, "Alimentación" to it.alimentacion,
                    "Adaptación" to it.adaptacion, "Observaciones" to it.observaciones
                ).filter { p -> !p.second.isNullOrBlank() }.joinToString("\n") { p -> "${p.first}: ${p.second}" }
                val ll = v.findViewById<LinearLayout>(R.id.llFotos)
                ll.removeAllViews()
                v.findViewById<View>(R.id.scrollFotos).visible(it.fotos.isNotEmpty())
                val size = v.dp(76)
                it.fotos.forEach { url ->
                    val iv = ShapeableImageView(v.context).apply {
                        layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = v.dp(8) }
                        shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCornerSizes(v.dp(12).toFloat()).build()
                        contentDescription = "Foto del reporte"
                    }
                    iv.loadPhoto(url)
                    ll.addView(iv)
                }
            }
            is CambioAdaptacion -> {
                v.findViewById<TextView>(R.id.tvTitulo).text = Format.estado(it.estado)
                v.findViewById<TextView>(R.id.tvFecha).text = Format.date(it.fechaCambio)
                v.findViewById<TextView>(R.id.tvResp).text = "Registrado por ${it.responsable}"
                v.findViewById<TextView>(R.id.tvNota).apply { visible(!it.observaciones.isNullOrBlank()); text = it.observaciones }
            }
        }
    }
}
