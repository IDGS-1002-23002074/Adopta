package com.baite.adopta

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.Publicacion
import com.baite.adopta.data.api
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.launch

/** RF038-RF042 — Comunidad: feed de publicaciones, Me gusta y comentarios. */
class CommunityFragment : Fragment(R.layout.fragment_community) {

    private val items = mutableListOf<Publicacion>()
    private var loading = false
    private var endReached = false
    private var generation = 0
    private lateinit var adapter: PostAdapter
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var progress: CircularProgressIndicator
    private lateinit var layoutEmpty: View

    private val editor = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) load(reset = true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipe = view.findViewById(R.id.swipe)
        progress = view.findViewById(R.id.progress)
        layoutEmpty = view.findViewById(R.id.layoutEmpty)

        val rv = view.findViewById<RecyclerView>(R.id.rvPosts)
        val lm = LinearLayoutManager(requireContext())
        rv.layoutManager = lm
        adapter = PostAdapter(onLike = ::toggleLike, onOpen = ::open, onMenu = ::showMenu)
        rv.adapter = adapter
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && lm.findLastVisibleItemPosition() >= adapter.itemCount - 3) load(reset = false)
            }
        })
        swipe.setColorSchemeResources(R.color.adopta_teal_dark)
        swipe.setOnRefreshListener { load(reset = true) }
        view.findViewById<ExtendedFloatingActionButton>(R.id.fabPublicar).setOnClickListener {
            editor.launch(Intent(requireContext(), PostEditorActivity::class.java))
        }
        load(reset = true)
    }

    override fun onResume() {
        super.onResume()
        // Al volver del detalle puede haber cambiado algo (Me gusta, comentarios, borrado).
        if (items.isNotEmpty()) load(reset = true, silent = true)
    }

    private fun load(reset: Boolean, silent: Boolean = false) {
        if (!reset && (loading || endReached)) return
        val gen = if (reset) ++generation else generation
        val antes = if (reset) null else items.lastOrNull()?.idPublicacion
        loading = true
        if (reset && items.isEmpty() && !swipe.isRefreshing) progress.visible(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val r = api { ApiClient.api.publicaciones(antes) }
            if (gen != generation) return@launch
            loading = false
            swipe.isRefreshing = false
            progress.visible(false)
            when (r) {
                is Outcome.Ok -> {
                    if (reset) { items.clear(); endReached = false }
                    items.addAll(r.value.items)
                    if (r.value.items.size < 15) endReached = true
                    adapter.submit(items.toList())
                }
                is Outcome.Fail -> if (!silent) requireView().showFail(r) { load(reset) }
            }
            layoutEmpty.visible(items.isEmpty() && r is Outcome.Ok)
        }
    }

    private fun open(p: Publicacion) {
        editor.launch(Intent(requireContext(), PostDetailActivity::class.java).putExtra(Extras.POST_ID, p.idPublicacion))
    }

    private fun replace(updated: Publicacion) {
        val i = items.indexOfFirst { it.idPublicacion == updated.idPublicacion }
        if (i >= 0) { items[i] = updated; adapter.submit(items.toList()) }
    }

    private fun toggleLike(p: Publicacion) {
        replace(p.copy(meGusta = !p.meGusta, likes = p.likes + if (p.meGusta) -1 else 1))
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = api { ApiClient.api.like(p.idPublicacion) }) {
                is Outcome.Ok -> replace(p.copy(meGusta = r.value.meGusta, likes = r.value.likes))
                is Outcome.Fail -> { replace(p); requireView().showFail(r) }
            }
        }
    }

    private fun showMenu(p: Publicacion, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(0, 1, 0, "Editar")
            menu.add(0, 2, 1, "Eliminar")
            setOnMenuItemClickListener {
                if (it.itemId == 1) editor.launch(Intent(requireContext(), PostEditorActivity::class.java).putExtra(Extras.POST_ID, p.idPublicacion))
                else confirmDelete(p)
                true
            }
        }.show()
    }

    private fun confirmDelete(p: Publicacion) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("¿Eliminar publicación?")
            .setMessage("Se borrarán también sus comentarios y Me gusta.")
            .setPositiveButton("Eliminar") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    when (val r = api { ApiClient.api.eliminarPublicacion(p.idPublicacion) }) {
                        is Outcome.Ok -> load(reset = true)
                        is Outcome.Fail -> requireView().showFail(r)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
