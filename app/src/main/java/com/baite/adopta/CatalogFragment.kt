package com.baite.adopta

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.Catalogos
import com.baite.adopta.data.Format
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.Pet
import com.baite.adopta.data.api
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * RF015-RF017 — Catálogo de todas las mascotas disponibles (no es una pantalla de "match").
 * Búsqueda por texto, filtros por especie, tamaño, edad, sexo, ubicación y raza; paginado.
 */
class CatalogFragment : Fragment(R.layout.fragment_catalog) {

    data class Filter(
        val query: String = "",
        val especie: String? = null,
        val tamano: String? = null,   // valor de la BD: Pequeno | Mediano | Grande
        val edad: Int? = null,        // índice en RANGOS_EDAD
        val sexo: String? = null,
        val ciudad: String? = null,
        val raza: String? = null
    ) {
        val avanzado get() = sexo != null || ciudad != null || raza != null
    }

    private var filter = Filter()
    private val items = mutableListOf<Pet>()
    private var total = 0
    private var loading = false
    private var generation = 0
    private var catalogos: Catalogos? = null

    private lateinit var adapter: CatalogAdapter
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var rv: RecyclerView
    private lateinit var progress: CircularProgressIndicator
    private lateinit var layoutEmpty: View
    private lateinit var tvContador: TextView
    private lateinit var etBuscar: EditText
    private lateinit var btnClearSearch: View
    private lateinit var chipTodas: Chip
    private lateinit var chipPerros: Chip
    private lateinit var chipGatos: Chip
    private lateinit var chipTamano: Chip
    private lateinit var chipEdad: Chip
    private lateinit var chipMas: Chip
    private lateinit var chipPrefs: Chip
    private var searchJob: Job? = null
    private var resumedOnce = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipe = view.findViewById(R.id.swipe)
        rv = view.findViewById(R.id.rvMascotas)
        progress = view.findViewById(R.id.progress)
        layoutEmpty = view.findViewById(R.id.layoutEmpty)
        tvContador = view.findViewById(R.id.tvContador)
        etBuscar = view.findViewById(R.id.etBuscar)
        btnClearSearch = view.findViewById(R.id.btnLimpiarBusqueda)
        chipTodas = view.findViewById(R.id.chipTodas)
        chipPerros = view.findViewById(R.id.chipPerros)
        chipGatos = view.findViewById(R.id.chipGatos)
        chipTamano = view.findViewById(R.id.chipTamano)
        chipEdad = view.findViewById(R.id.chipEdad)
        chipMas = view.findViewById(R.id.chipMas)
        chipPrefs = view.findViewById(R.id.chipPrefs)

        val dm = resources.displayMetrics
        val span = (dm.widthPixels / dm.density / 180f).toInt().coerceAtLeast(2)
        val lm = GridLayoutManager(requireContext(), span)
        rv.layoutManager = lm
        adapter = CatalogAdapter(onClick = ::openDetail, onSave = ::toggleSave)
        rv.adapter = adapter
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && lm.findLastVisibleItemPosition() >= adapter.itemCount - 4) load(reset = false)
            }
        })
        swipe.setColorSchemeResources(R.color.adopta_teal_dark)
        swipe.setOnRefreshListener { load(reset = true) }

        view.findViewById<View>(R.id.btnGuardados).setOnClickListener {
            startActivity(Intent(requireContext(), FavoritesActivity::class.java))
        }

        etBuscar.doAfterTextChanged {
            val q = it?.toString().orEmpty()
            btnClearSearch.visible(q.isNotEmpty())
            if (q.trim() != filter.query) {
                searchJob?.cancel()
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(400) // espera a que termine de escribir
                    applyFilter(filter.copy(query = q.trim()))
                }
            }
        }
        etBuscar.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
                v.clearFocus()
                true
            } else false
        }
        btnClearSearch.setOnClickListener { etBuscar.text.clear() }

        chipTodas.setOnClickListener { applyFilter(filter.copy(especie = null)) }
        chipPerros.setOnClickListener { applyFilter(filter.copy(especie = if (filter.especie == "Perro") null else "Perro")) }
        chipGatos.setOnClickListener { applyFilter(filter.copy(especie = if (filter.especie == "Gato") null else "Gato")) }
        chipTamano.setOnClickListener {
            val labels = TAMANOS.map { Format.tamano(it) }
            menu(chipTamano, labels, TAMANOS.indexOf(filter.tamano), "Todos los tamaños") { i -> applyFilter(filter.copy(tamano = i?.let { TAMANOS[it] })) }
        }
        chipEdad.setOnClickListener {
            menu(chipEdad, RANGOS_EDAD, filter.edad ?: -1, "Todas las edades") { i -> applyFilter(filter.copy(edad = i)) }
        }
        chipMas.setOnClickListener { showFilters() }
        chipPrefs.setOnClickListener { togglePrefs() }
        view.findViewById<MaterialButton>(R.id.btnLimpiar).setOnClickListener {
            if (errorState) load(reset = true) else {
                etBuscar.text.clear()
                applyFilter(Filter())
            }
        }

        refreshChips()
        load(reset = true)
        viewLifecycleOwner.lifecycleScope.launch {
            (api { ApiClient.api.catalogos() } as? Outcome.Ok)?.let { catalogos = it.value }
        }
    }

    override fun onResume() {
        super.onResume()
        if (resumedOnce && view != null) syncFavorites()
        resumedOnce = true
    }

    // ---------------------------------------------------------------- carga

    private var errorState = false

    private fun applyFilter(f: Filter) {
        filter = f
        refreshChips()
        load(reset = true)
    }

    private fun load(reset: Boolean) {
        if (!reset && (loading || items.size >= total)) return
        val gen = if (reset) ++generation else generation
        val offset = if (reset) 0 else items.size
        val f = filter
        loading = true
        if (reset && items.isEmpty() && !swipe.isRefreshing) progress.visible(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val r = api {
                ApiClient.api.mascotas(
                    q = f.query.ifBlank { null }, especie = f.especie, raza = f.raza, tamano = f.tamano,
                    sexo = f.sexo, ciudad = f.ciudad, edad = f.edad, offset = offset
                )
            }
            if (gen != generation) return@launch // llegó una respuesta vieja: ya hay otra búsqueda
            loading = false
            swipe.isRefreshing = false
            progress.visible(false)
            when (r) {
                is Outcome.Ok -> {
                    errorState = false
                    if (reset) items.clear()
                    items.addAll(r.value.items)
                    total = r.value.total
                    adapter.submit(items.toList())
                }
                is Outcome.Fail -> {
                    if (reset) {
                        errorState = true
                        items.clear()
                        adapter.submit(emptyList())
                        showEmpty(r.message, retry = true)
                        tvContador.text = "No se pudo cargar"
                        return@launch
                    }
                    requireView().showFail(r) { load(reset = false) }
                }
            }
            updateEmpty()
        }
    }

    private fun updateEmpty() {
        val empty = items.isEmpty()
        layoutEmpty.visible(empty)
        swipe.visibility = if (empty) View.INVISIBLE else View.VISIBLE
        tvContador.text = when {
            empty -> "Sin resultados"
            total == 1 -> "1 mascota disponible"
            else -> "$total mascotas disponibles"
        }
        if (empty) showEmpty("Prueba con otra búsqueda o quita algunos filtros.", retry = false)
    }

    private fun showEmpty(message: String, retry: Boolean) {
        layoutEmpty.visible(true)
        swipe.visibility = View.INVISIBLE
        layoutEmpty.findViewById<TextView>(R.id.tvEmptyTitle).text = if (retry) "No pudimos cargar el catálogo" else "No encontramos mascotas"
        layoutEmpty.findViewById<TextView>(R.id.tvEmptyMsg).text = message
        layoutEmpty.findViewById<MaterialButton>(R.id.btnLimpiar).text = if (retry) "Reintentar" else "Limpiar filtros"
    }

    // ---------------------------------------------------------------- filtros

    private fun refreshChips() {
        chipTodas.isChecked = filter.especie == null
        chipPerros.isChecked = filter.especie == "Perro"
        chipGatos.isChecked = filter.especie == "Gato"
        chipTamano.isChecked = filter.tamano != null
        chipTamano.text = (filter.tamano?.let { Format.tamano(it) } ?: "Tamaño") + " ▾"
        chipEdad.isChecked = filter.edad != null
        chipEdad.text = (filter.edad?.let { RANGOS_EDAD[it] } ?: "Edad") + " ▾"
        chipMas.isChecked = filter.avanzado
        chipMas.text = if (filter.avanzado) "Más filtros ●" else "Más filtros"
    }

    private fun menu(anchor: View, options: List<String>, selected: Int, allLabel: String, onPick: (Int?) -> Unit) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, ALL_ID, 0, allLabel).isChecked = selected < 0
        options.forEachIndexed { i, label -> popup.menu.add(0, i, i + 1, label).isChecked = i == selected }
        popup.menu.setGroupCheckable(0, true, true)
        popup.setOnMenuItemClickListener { item ->
            onPick(if (item.itemId == ALL_ID) null else item.itemId)
            true
        }
        popup.setOnDismissListener { refreshChips() } // el toque alterna el chip; se restablece al estado real
        popup.show()
    }

    private fun showFilters() {
        val cat = catalogos
        if (cat == null) {
            viewLifecycleOwner.lifecycleScope.launch {
                when (val r = api { ApiClient.api.catalogos() }) {
                    is Outcome.Ok -> { catalogos = r.value; showFilters() }
                    is Outcome.Fail -> requireView().showFail(r) { showFilters() }
                }
            }
            return
        }
        val dialog = BottomSheetDialog(requireContext())
        val v = layoutInflater.inflate(R.layout.sheet_filters, null)
        dialog.setContentView(v)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        val any = "Cualquiera"
        fun bindDropdown(id: Int, options: List<String>, current: String?): MaterialAutoCompleteTextView =
            v.findViewById<MaterialAutoCompleteTextView>(id).apply {
                setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, listOf(any) + options))
                setText(current ?: any, false)
            }
        val dSexo = bindDropdown(R.id.etSexo, cat.sexos, filter.sexo)
        val dCiudad = bindDropdown(R.id.etCiudad, cat.ciudades, filter.ciudad)
        val dRaza = bindDropdown(R.id.etRaza, cat.razas, filter.raza)
        fun value(d: MaterialAutoCompleteTextView) = d.text.toString().takeIf { it != any && it.isNotBlank() }
        v.findViewById<View>(R.id.btnAplicar).setOnClickListener {
            applyFilter(filter.copy(sexo = value(dSexo), ciudad = value(dCiudad), raza = value(dRaza)))
            dialog.dismiss()
        }
        v.findViewById<View>(R.id.btnLimpiarFiltros).setOnClickListener {
            applyFilter(filter.copy(sexo = null, ciudad = null, raza = null))
            dialog.dismiss()
        }
        dialog.show()
    }

    /** RF005 — aplica las preferencias de búsqueda guardadas en el perfil. */
    private fun togglePrefs() {
        if (!chipPrefs.isChecked) {
            applyFilter(filter.copy(especie = null, tamano = null, edad = null))
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = api { ApiClient.api.preferencias() }) {
                is Outcome.Ok -> {
                    val p = r.value
                    if (p.especie == null && p.tamano == null && p.edadPreferida == null) {
                        chipPrefs.isChecked = false
                        requireView().snack("Aún no tienes preferencias. Configúralas en Perfil > Preferencias.")
                    } else {
                        applyFilter(filter.copy(especie = p.especie, tamano = p.tamano, edad = RANGOS_EDAD.indexOf(p.edadPreferida).takeIf { it >= 0 }))
                    }
                }
                is Outcome.Fail -> {
                    chipPrefs.isChecked = false
                    requireView().showFail(r)
                }
            }
        }
    }

    // ---------------------------------------------------------------- acciones

    private fun openDetail(p: Pet) {
        startActivity(Intent(requireContext(), PetDetailActivity::class.java).putExtra(Extras.PET_ID, p.idMascota))
    }

    private fun setFavoriteLocal(id: Int, value: Boolean) {
        val i = items.indexOfFirst { it.idMascota == id }
        if (i >= 0 && items[i].esFavorito != value) {
            items[i] = items[i].copy(esFavorito = value)
            adapter.submit(items.toList())
        }
    }

    private fun toggleSave(p: Pet) {
        val nuevo = !p.esFavorito
        setFavoriteLocal(p.idMascota, nuevo)
        viewLifecycleOwner.lifecycleScope.launch {
            val r = api { if (nuevo) ApiClient.api.addFavorito(p.idMascota) else ApiClient.api.removeFavorito(p.idMascota) }
            if (r is Outcome.Fail) {
                setFavoriteLocal(p.idMascota, !nuevo)
                requireView().showFail(r)
                return@launch
            }
            val ctx = requireContext()
            Snackbar.make(requireView(), if (nuevo) "${p.nombre} está en tus guardados" else "Quitaste a ${p.nombre} de guardados", Snackbar.LENGTH_SHORT)
                .setBackgroundTint(ContextCompat.getColor(ctx, R.color.adopta_text))
                .setTextColor(ContextCompat.getColor(ctx, R.color.white))
                .setActionTextColor(ContextCompat.getColor(ctx, R.color.adopta_mint))
                .apply { if (nuevo) setAction("Ver") { startActivity(Intent(ctx, FavoritesActivity::class.java)) } }
                .show()
        }
    }

    /** Al volver de otra pantalla, refleja los guardados que cambiaron allá. */
    private fun syncFavorites() {
        viewLifecycleOwner.lifecycleScope.launch {
            val r = api { ApiClient.api.favoritos() } as? Outcome.Ok ?: return@launch
            val ids = r.value.items.map { it.idMascota }.toSet()
            var changed = false
            for (i in items.indices) {
                val fav = items[i].idMascota in ids
                if (items[i].esFavorito != fav) { items[i] = items[i].copy(esFavorito = fav); changed = true }
            }
            if (changed) adapter.submit(items.toList())
        }
    }

    companion object {
        private const val ALL_ID = 100
        val TAMANOS = listOf("Pequeno", "Mediano", "Grande")
        val RANGOS_EDAD = listOf("Menos de 1 año", "1 a 3 años", "4 años o más")
    }
}
