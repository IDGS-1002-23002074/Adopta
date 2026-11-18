package com.baite.adopta

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.Format
import com.baite.adopta.data.Notificacion
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.Session
import com.baite.adopta.data.api
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.launch

/** RF043 — Notificaciones: filtro por tipo, marcar como leídas y abrir la pantalla relacionada. */
class NotificationsFragment : Fragment(R.layout.fragment_notifications) {

    private var tipo: String? = null
    private var items = emptyList<Notificacion>()
    private var loaded = false
    private lateinit var adapter: NotificationAdapter
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var progress: CircularProgressIndicator
    private lateinit var layoutEmpty: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipe = view.findViewById(R.id.swipe)
        progress = view.findViewById(R.id.progress)
        layoutEmpty = view.findViewById(R.id.layoutEmpty)

        val rv = view.findViewById<RecyclerView>(R.id.rvNotificaciones)
        rv.layoutManager = LinearLayoutManager(requireContext())
        adapter = NotificationAdapter(::open)
        rv.adapter = adapter
        swipe.setColorSchemeResources(R.color.adopta_teal_dark)
        swipe.setOnRefreshListener { load() }

        val chips = mapOf(
            R.id.chipTodas to null, R.id.chipSolicitudes to "Solicitudes", R.id.chipDocumentos to "Documentos",
            R.id.chipSeguimiento to "Seguimiento", R.id.chipComunidad to "Comunidad", R.id.chipDevoluciones to "Devoluciones"
        )
        fun refreshChips() = chips.forEach { (id, t) -> view.findViewById<Chip>(id).isChecked = t == tipo }
        chips.forEach { (id, t) ->
            view.findViewById<Chip>(id).setOnClickListener { tipo = t; refreshChips(); load() }
        }
        refreshChips()

        view.findViewById<MaterialButton>(R.id.btnLeerTodas).setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                when (val r = api { ApiClient.api.leerTodas() }) {
                    is Outcome.Ok -> { items = items.map { it.copy(leida = true) }; show(); (activity as? HomeActivity)?.refreshBadge() }
                    is Outcome.Fail -> requireView().showFail(r)
                }
            }
        }
        load()
    }

    override fun onResume() {
        super.onResume()
        if (loaded) load(silent = true)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && view != null) load(silent = true)
    }

    private fun load(silent: Boolean = false) {
        if (!loaded && !silent) progress.visible(true)
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = api { ApiClient.api.notificaciones(tipo) }) {
                is Outcome.Ok -> { items = r.value.items; loaded = true; show() }
                is Outcome.Fail -> if (!silent) requireView().showFail(r) { load() }
            }
            progress.visible(false)
            swipe.isRefreshing = false
            (activity as? HomeActivity)?.refreshBadge()
        }
    }

    private fun show() {
        // RF005: se ocultan las categorías que la persona desactivó en Preferencias
        val list = items.filter { it.tipo == null || Session.notifEnabled(it.tipo) }
        adapter.submit(list)
        layoutEmpty.visible(list.isEmpty())
        swipe.visibility = if (list.isEmpty()) View.INVISIBLE else View.VISIBLE
    }

    private fun open(n: Notificacion) {
        if (!n.leida) {
            items = items.map { if (it.idNotificacion == n.idNotificacion) it.copy(leida = true) else it }
            show()
            viewLifecycleOwner.lifecycleScope.launch {
                api { ApiClient.api.marcarLeida(n.idNotificacion) }
                (activity as? HomeActivity)?.refreshBadge()
            }
        }
        val ctx = requireContext()
        val home = activity as? HomeActivity
        when {
            n.tipo == "Documentos" && n.idSolicitud != null && n.estadoSolicitud == "Documentacion pendiente" ->
                startActivity(Intent(ctx, DocumentationActivity::class.java).putExtra(Extras.REQUEST_ID, n.idSolicitud))
            n.idSolicitud != null ->
                startActivity(Intent(ctx, RequestDetailActivity::class.java).putExtra(Extras.REQUEST_ID, n.idSolicitud))
            n.tipo == "Seguimiento" -> home?.openHome(MyHomeFragment.SECTION_MASCOTAS)
            n.tipo == "Devoluciones" -> home?.openHome(MyHomeFragment.SECTION_DEVOLUCIONES)
            n.tipo == "Comunidad" -> home?.selectTab(R.id.nav_comunidad)
        }
    }
}

class NotificationAdapter(private val onClick: (Notificacion) -> Unit) : RecyclerView.Adapter<NotificationAdapter.VH>() {
    private var items = emptyList<Notificacion>()
    class VH(v: View) : RecyclerView.ViewHolder(v)

    fun submit(list: List<Notificacion>) { items = list; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val n = items[position]
        val v = h.itemView
        v.findViewById<TextView>(R.id.tvTitulo).text = n.titulo
        v.findViewById<TextView>(R.id.tvMensaje).text = n.mensaje
        v.findViewById<TextView>(R.id.tvTiempo).text = Format.relative(n.fechaEnvio)
        v.findViewById<View>(R.id.vNoLeida).visible(!n.leida)
        v.findViewById<ImageView>(R.id.ivTipo).setImageResource(
            when (n.tipo) {
                "Solicitudes" -> R.drawable.ic_check
                "Documentos" -> R.drawable.ic_shield_check
                "Seguimiento" -> R.drawable.ic_camera
                "Comunidad" -> R.drawable.ic_forum
                "Devoluciones" -> R.drawable.ic_home_outline
                else -> R.drawable.ic_notifications
            }
        )
        v.alpha = if (n.leida) 0.8f else 1f
        v.setOnClickListener { onClick(n) }
    }

    override fun getItemCount() = items.size
}
