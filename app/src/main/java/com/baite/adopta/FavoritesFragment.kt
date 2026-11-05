package com.baite.adopta

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** "Guardados": cuadrícula de 2 columnas con foto protagonista. */
class FavoritesFragment : Fragment(R.layout.fragment_favorites) {

    private lateinit var adapter: FavoritesAdapter
    private var empty: View? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        empty = view.findViewById(R.id.layoutEmpty)

        val rv = view.findViewById<RecyclerView>(R.id.rvFavoritos)
        rv.layoutManager = GridLayoutManager(requireContext(), 2)
        adapter = FavoritesAdapter(
            SampleData.favoritos,
            onClick = { pet ->
                startActivity(
                    Intent(requireContext(), PetDetailActivity::class.java)
                        .putExtra(Extras.PET_ID, pet.id)
                )
            },
            onUnsave = { pet ->
                SampleData.guardadosIds.remove(pet.id)
                refresh()
            }
        )
        rv.adapter = adapter

        view.findViewById<View>(R.id.btnExplorar).setOnClickListener {
            (activity as? HomeActivity)?.selectTab(R.id.nav_descubrir)
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (view != null) refresh()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && view != null) refresh()
    }

    private fun refresh() {
        val list = SampleData.favoritos
        adapter.submit(list)
        empty?.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }
}
