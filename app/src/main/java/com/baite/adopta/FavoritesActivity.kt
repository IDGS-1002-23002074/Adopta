package com.baite.adopta

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.Pet
import com.baite.adopta.data.api
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.launch

/** RF019 — Mascotas guardadas. */
class FavoritesActivity : BaseActivity() {

    private val items = mutableListOf<Pet>()
    private lateinit var adapter: CatalogAdapter
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var layoutEmpty: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        swipe = findViewById(R.id.swipe)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        findViewById<TextView>(R.id.tvEmptyTitle).text = "Aún no guardas mascotas"
        findViewById<TextView>(R.id.tvEmptyMsg).text = "Toca el marcador en una mascota para encontrarla aquí después."
        findViewById<MaterialButton>(R.id.btnLimpiar).apply { text = "Explorar mascotas"; setOnClickListener { finish() } }

        val rv = findViewById<RecyclerView>(R.id.rvMascotas)
        val dm = resources.displayMetrics
        rv.layoutManager = GridLayoutManager(this, (dm.widthPixels / dm.density / 180f).toInt().coerceAtLeast(2))
        adapter = CatalogAdapter(
            onClick = { startActivity(Intent(this, PetDetailActivity::class.java).putExtra(Extras.PET_ID, it.idMascota)) },
            onSave = ::remove
        )
        rv.adapter = adapter
        swipe.setColorSchemeResources(R.color.adopta_teal_dark)
        swipe.setOnRefreshListener { load() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val progress = findViewById<CircularProgressIndicator>(R.id.progress)
        if (items.isEmpty()) progress.visible(true)
        lifecycleScope.launch {
            when (val r = api { ApiClient.api.favoritos() }) {
                is Outcome.Ok -> {
                    items.clear()
                    items.addAll(r.value.items)
                    adapter.submit(items.toList())
                    layoutEmpty.visible(items.isEmpty())
                    swipe.visibility = if (items.isEmpty()) View.INVISIBLE else View.VISIBLE
                }
                is Outcome.Fail -> findViewById<View>(android.R.id.content).showFail(r) { load() }
            }
            progress.visible(false)
            swipe.isRefreshing = false
        }
    }

    private fun remove(p: Pet) {
        val index = items.indexOfFirst { it.idMascota == p.idMascota }
        if (index < 0) return
        items.removeAt(index)
        adapter.submit(items.toList())
        layoutEmpty.visible(items.isEmpty())
        lifecycleScope.launch {
            val r = api { ApiClient.api.removeFavorito(p.idMascota) }
            if (r is Outcome.Fail) {
                items.add(index.coerceAtMost(items.size), p)
                adapter.submit(items.toList())
                layoutEmpty.visible(false)
                findViewById<View>(android.R.id.content).showFail(r)
            }
        }
    }
}
