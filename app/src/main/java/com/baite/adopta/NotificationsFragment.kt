package com.baite.adopta

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup

class NotificationsFragment : Fragment(R.layout.fragment_notifications) {

    private lateinit var adapter: NotificationsAdapter
    private lateinit var empty: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        empty = view.findViewById(R.id.layoutEmpty)

        adapter = NotificationsAdapter(SampleData.notificaciones) { n ->
            startActivity(
                Intent(requireContext(), NotificationDetailActivity::class.java)
                    .putExtra(Extras.NOTIF_ID, n.id)
            )
        }
        val rv = view.findViewById<RecyclerView>(R.id.rvNotificaciones)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.layoutAnimation = AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_fade_up)
        rv.adapter = adapter

        view.findViewById<ChipGroup>(R.id.chipGroupFiltro).setOnCheckedStateChangeListener { _, ids ->
            val tipo = when (ids.firstOrNull()) {
                R.id.chipSolicitudes -> "Solicitudes"
                R.id.chipSeguimiento -> "Seguimiento"
                R.id.chipComunidad -> "Comunidad"
                else -> null
            }
            adapter.filter(tipo)
            updateEmpty()
        }
        showLocal()
    }

    override fun onResume() {
        super.onResume()
        if (view != null) refresh()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && view != null) refresh()
    }

    /** Muestra lo que hay en memoria y pide la lista fresca a la API. */
    private fun refresh() {
        showLocal()
        NotificationRepository.load {
            if (view == null) return@load
            showLocal()
            (activity as? HomeActivity)?.refreshBadge()
        }
    }

    private fun showLocal() {
        adapter.submit(SampleData.notificaciones)
        updateEmpty()
    }

    private fun updateEmpty() {
        empty.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
    }
}
