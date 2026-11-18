package com.baite.adopta

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.baite.adopta.data.AdopcionItem
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.Devolucion
import com.baite.adopta.data.Format
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.SolicitudItem
import com.baite.adopta.data.api
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/** Mi hogar: mis mascotas adoptadas (seguimiento), mis solicitudes de adopción y mis devoluciones. */
class MyHomeFragment : Fragment(R.layout.fragment_my_home) {

    private var section = SECTION_MASCOTAS
    private var adopciones = emptyList<AdopcionItem>()
    private var solicitudes = emptyList<SolicitudItem>()
    private var devoluciones = emptyList<Devolucion>()
    private var loaded = false

    private lateinit var adapter: HomeAdapter
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var progress: CircularProgressIndicator
    private lateinit var layoutEmpty: View
    private lateinit var group: MaterialButtonToggleGroup

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipe = view.findViewById(R.id.swipe)
        progress = view.findViewById(R.id.progress)
        layoutEmpty = view.findViewById(R.id.layoutEmpty)
        group = view.findViewById(R.id.segmentos)

        val rv = view.findViewById<RecyclerView>(R.id.rvHogar)
        rv.layoutManager = LinearLayoutManager(requireContext())
        adapter = HomeAdapter(
            onAdopcion = { startActivity(Intent(requireContext(), TrackingDetailActivity::class.java).putExtra(Extras.ADOPTION_ID, it.idAdopcion)) },
            onSolicitud = { startActivity(Intent(requireContext(), RequestDetailActivity::class.java).putExtra(Extras.REQUEST_ID, it.idSolicitud)) },
            onDevolucion = ::showReturn
        )
        rv.adapter = adapter
        swipe.setColorSchemeResources(R.color.adopta_teal_dark)
        swipe.setOnRefreshListener { load() }

        group.check(R.id.btnSegMascotas)
        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            section = when (checkedId) {
                R.id.btnSegSolicitudes -> SECTION_SOLICITUDES
                R.id.btnSegDevoluciones -> SECTION_DEVOLUCIONES
                else -> SECTION_MASCOTAS
            }
            showSection()
        }
        view.findViewById<MaterialButton>(R.id.btnEmptyAction).setOnClickListener {
            (activity as? HomeActivity)?.selectTab(R.id.nav_mascotas)
        }
        showRequestedSection()
    }

    override fun onResume() {
        super.onResume()
        showRequestedSection()
        load()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && view != null) { showRequestedSection(); load() }
    }

    fun showRequestedSection() {
        val s = requestedSection
        if (s < 0 || view == null) return
        requestedSection = -1
        group.check(when (s) { SECTION_SOLICITUDES -> R.id.btnSegSolicitudes; SECTION_DEVOLUCIONES -> R.id.btnSegDevoluciones; else -> R.id.btnSegMascotas })
    }

    private fun load() {
        if (!loaded) progress.visible(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val a = async { api { ApiClient.api.adopciones() } }
            val s = async { api { ApiClient.api.solicitudes() } }
            val d = async { api { ApiClient.api.devoluciones() } }
            val ra = a.await(); val rs = s.await(); val rd = d.await()
            progress.visible(false)
            swipe.isRefreshing = false
            val fail = listOf(ra, rs, rd).filterIsInstance<Outcome.Fail>().firstOrNull()
            if (fail != null && !loaded) {
                requireView().showFail(fail) { load() }
            } else if (fail != null) {
                requireView().showFail(fail)
            }
            (ra as? Outcome.Ok)?.let { adopciones = it.value.items }
            (rs as? Outcome.Ok)?.let { solicitudes = it.value.items }
            (rd as? Outcome.Ok)?.let { devoluciones = it.value.items }
            if (fail == null) loaded = true
            showSection()
        }
    }

    private fun showSection() {
        view ?: return
        requireView().findViewById<MaterialButton>(R.id.btnSegSolicitudes).text =
            "Solicitudes" + solicitudes.count { it.estado in ACTIVAS }.let { if (it > 0) " ($it)" else "" }
        val list: List<Any> = when (section) {
            SECTION_SOLICITUDES -> solicitudes
            SECTION_DEVOLUCIONES -> devoluciones
            else -> adopciones
        }
        adapter.submit(list)
        val empty = list.isEmpty() && loaded
        layoutEmpty.visible(empty)
        swipe.visibility = if (empty) View.INVISIBLE else View.VISIBLE
        if (empty) {
            val (title, msg) = when (section) {
                SECTION_SOLICITUDES -> "Aún no tienes solicitudes" to "Cuando solicites adoptar una mascota, podrás seguir aquí cada paso."
                SECTION_DEVOLUCIONES -> "Sin devoluciones" to "Aquí verás el estado si alguna vez necesitas devolver una mascota."
                else -> "Aún no tienes mascotas adoptadas" to "Cuando una adopción se apruebe, aparecerá aquí con su seguimiento."
            }
            layoutEmpty.findViewById<TextView>(R.id.tvEmptyTitle).text = title
            layoutEmpty.findViewById<TextView>(R.id.tvEmptyMsg).text = msg
            layoutEmpty.findViewById<View>(R.id.btnEmptyAction).visible(section != SECTION_DEVOLUCIONES)
        }
    }

    private fun showReturn(d: Devolucion) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Devolución de ${d.mascota}")
            .setMessage(buildString {
                append("Estado: ${Format.estado(d.estado)}\nSolicitada el ${Format.date(d.fechaSolicitud)}")
                if (d.motivos.isNotEmpty()) append("\n\nMotivos: ${d.motivos.joinToString(", ")}")
                append("\n\n${d.explicacion}")
                if (!d.observacionesOrganizacion.isNullOrBlank()) append("\n\nRespuesta de ${d.organizacion}: ${d.observacionesOrganizacion}")
            })
            .setPositiveButton("Cerrar", null)
            .show()
    }

    companion object {
        const val SECTION_MASCOTAS = 0
        const val SECTION_SOLICITUDES = 1
        const val SECTION_DEVOLUCIONES = 2
        var requestedSection = -1
        private val ACTIVAS = listOf("Pendiente", "En revision", "Documentacion pendiente")
    }
}

/** Una sola lista con tres tipos de tarjeta según la sección. */
class HomeAdapter(
    private val onAdopcion: (AdopcionItem) -> Unit,
    private val onSolicitud: (SolicitudItem) -> Unit,
    private val onDevolucion: (Devolucion) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<Any> = emptyList()
    class VH(v: View) : RecyclerView.ViewHolder(v)

    fun submit(list: List<Any>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is AdopcionItem -> 0
        is SolicitudItem -> 1
        else -> 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = when (viewType) { 0 -> R.layout.item_adopcion; 1 -> R.layout.item_solicitud; else -> R.layout.item_devolucion }
        return VH(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
        val v = h.itemView
        when (val it = items[position]) {
            is AdopcionItem -> {
                v.findViewById<ImageView>(R.id.ivFoto).loadPhoto(it.foto, it.idMascota)
                v.findViewById<TextView>(R.id.tvNombre).text = it.nombre
                v.findViewById<TextView>(R.id.tvSub).text = "Adoptada el ${Format.date(it.fechaAdopcion)} · ${it.organizacion}"
                val tvEstado = v.findViewById<TextView>(R.id.tvEstado)
                if (it.estadoDevolucion != null) tvEstado.applyTone("Devolución: ${Format.estado(it.estadoDevolucion)}", Format.Tone.WARN)
                else tvEstado.applyStatus(it.estadoAdaptacion)
                v.findViewById<LinearProgressIndicator>(R.id.progAdaptacion).setProgressCompat(Format.adaptacionProgreso(it.estadoAdaptacion), false)
                v.findViewById<TextView>(R.id.tvUltimo).text =
                    if (it.ultimoReporte != null) "Último reporte: ${Format.relative(it.ultimoReporte)}" else "Aún no registras reportes"
                v.setOnClickListener { _ -> onAdopcion(it) }
            }
            is SolicitudItem -> {
                v.findViewById<ImageView>(R.id.ivFoto).loadPhoto(it.fotoMascota, it.idMascota)
                v.findViewById<TextView>(R.id.tvNombre).text = it.mascota
                v.findViewById<TextView>(R.id.tvSub).text = "${it.organizacion} · ${Format.date(it.fechaSolicitud)}"
                v.findViewById<TextView>(R.id.tvEstado).applyStatus(it.estado)
                v.findViewById<TextView>(R.id.tvAccion).apply {
                    visible(it.requiereDocumentos)
                    text = "Sube tus documentos" + (it.fechaLimiteDocumentos?.let { f -> " antes del ${Format.date(f)}" } ?: "")
                }
                v.setOnClickListener { _ -> onSolicitud(it) }
            }
            is Devolucion -> {
                v.findViewById<ImageView>(R.id.ivFoto).loadPhoto(it.foto, it.idMascota)
                v.findViewById<TextView>(R.id.tvNombre).text = it.mascota
                v.findViewById<TextView>(R.id.tvSub).text = "${it.organizacion} · ${Format.date(it.fechaSolicitud)}"
                v.findViewById<TextView>(R.id.tvEstado).applyStatus(it.estado)
                v.setOnClickListener { _ -> onDevolucion(it) }
            }
        }
    }

    override fun getItemCount() = items.size
}
