package com.baite.adopta

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.Format
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.PetDetail
import com.baite.adopta.data.api
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import java.util.Locale

/** RF017 — Detalle completo de la mascota, con galería, organización y botón para solicitar adopción. */
class PetDetailActivity : BaseActivity() {

    override val applyInsets = false

    private var petId = -1
    private var pet: PetDetail? = null
    private lateinit var btnSave: ImageButton
    private lateinit var btnAdoptar: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pet_detail)
        petId = intent.getIntExtra(Extras.PET_ID, -1)
        if (petId < 0) {
            finish()
            return
        }

        // Foto a sangre bajo la barra de estado: iconos claros y botones desplazados por el inset.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        val btnBack = findViewById<View>(R.id.btnBack)
        btnSave = findViewById(R.id.btnFavorito)
        btnAdoptar = findViewById(R.id.btnAdoptar)
        val bottomBar = findViewById<View>(R.id.bottomBar)
        val scroll = findViewById<View>(R.id.scroll)
        val baseBarPad = bottomBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detailRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (btnBack.layoutParams as ViewGroup.MarginLayoutParams).topMargin = bars.top + dp(12)
            (btnSave.layoutParams as ViewGroup.MarginLayoutParams).topMargin = bars.top + dp(12)
            btnBack.requestLayout()
            btnSave.requestLayout()
            bottomBar.setPadding(bottomBar.paddingLeft, bottomBar.paddingTop, bottomBar.paddingRight, baseBarPad + bars.bottom)
            scroll.setPadding(0, 0, 0, dp(88) + bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val rv = findViewById<RecyclerView>(R.id.rvFotos)
        rv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        PagerSnapHelper().attachToRecyclerView(rv)
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                val lm = recyclerView.layoutManager as LinearLayoutManager
                val pos = lm.findFirstCompletelyVisibleItemPosition()
                if (pos >= 0) findViewById<TextView>(R.id.tvIndice).text = "${pos + 1}/${recyclerView.adapter?.itemCount ?: 1}"
            }
        })

        btnBack.setOnClickListener { finish() }
        btnSave.setOnClickListener { toggleSave() }
        findViewById<View>(R.id.bottomBar).visibility = View.INVISIBLE
        load()
    }

    private fun load() {
        findViewById<View>(R.id.progress).visible(true)
        lifecycleScope.launch {
            when (val r = api { ApiClient.api.mascota(petId) }) {
                is Outcome.Ok -> {
                    pet = r.value
                    bind(r.value)
                }
                is Outcome.Fail -> findViewById<View>(R.id.detailRoot).showFail(r) { load() }
            }
            findViewById<View>(R.id.progress).visible(false)
        }
    }

    private fun bind(p: PetDetail) {
        val urls: List<String?> = if (p.fotos.isEmpty()) listOf(null) else p.fotos
        findViewById<RecyclerView>(R.id.rvFotos).adapter = PhotoPagerAdapter(urls, p.idMascota)
        findViewById<TextView>(R.id.tvIndice).apply {
            visible(urls.size > 1)
            text = "1/${urls.size}"
        }
        findViewById<TextView>(R.id.tvNombre).text = p.nombre
        findViewById<TextView>(R.id.tvRaza).text = listOfNotNull(p.raza, p.especie).joinToString(" · ")
        findViewById<TextView>(R.id.tvUbicacion).text = p.ciudad ?: p.organizacionDetalle.ciudad ?: ""
        findViewById<TextView>(R.id.chipEstado).apply {
            applyStatus(p.estadoMascota)
            TextViewCompat.setCompoundDrawableTintList(this, android.content.res.ColorStateList.valueOf(currentTextColor))
        }
        findViewById<TextView>(R.id.tileEdad).text = p.edadAproximada ?: "—"
        findViewById<TextView>(R.id.tileTamano).text = Format.tamano(p.tamano).ifBlank { "—" }
        findViewById<TextView>(R.id.tileSexo).text = p.sexo.ifBlank { "—" }
        findViewById<TextView>(R.id.tvDescripcion).text = p.descripcion?.takeIf { it.isNotBlank() } ?: "${p.nombre} busca un hogar lleno de cariño."

        val comp = p.comportamiento?.takeIf { it.isNotBlank() }
        findViewById<View>(R.id.tvComportamientoTitulo).visible(comp != null)
        findViewById<TextView>(R.id.tvComportamiento).apply { visible(comp != null); text = comp }

        val tags = listOfNotNull(p.color, p.peso?.let { String.format(Locale.getDefault(), "%.1f kg", it) })
        val group = findViewById<ChipGroup>(R.id.cgPersonalidad)
        group.removeAllViews()
        tags.forEach { tag ->
            val tv = LayoutInflater.from(this).inflate(R.layout.item_tag, group, false) as TextView
            tv.text = tag
            group.addView(tv)
        }
        group.visible(tags.isNotEmpty())
        findViewById<View>(R.id.tvPersonalidadTitulo).visible(tags.isNotEmpty())

        findViewById<View>(R.id.chipVacunada).visible(p.vacunado)
        findViewById<View>(R.id.chipEsterilizada).visible(p.esterilizado)
        val salud = p.estadoSalud?.takeIf { it.isNotBlank() }
        findViewById<TextView>(R.id.tvEstadoSalud).apply { visible(salud != null); text = salud }

        val org = p.organizacionDetalle
        findViewById<ImageView>(R.id.ivRefugio).loadAvatar(org.logo)
        findViewById<TextView>(R.id.tvRefugio).text = org.nombre
        findViewById<TextView>(R.id.tvRespuesta).text = listOfNotNull(org.ciudad, org.direccion).joinToString(" · ")
        findViewById<MaterialButton>(R.id.btnLlamar).apply {
            visible(!org.telefono.isNullOrBlank())
            setOnClickListener { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${org.telefono}"))) }
        }
        findViewById<MaterialButton>(R.id.btnCorreo).apply {
            visible(!org.correoContacto.isNullOrBlank())
            setOnClickListener {
                startActivity(
                    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${org.correoContacto}"))
                        .putExtra(Intent.EXTRA_SUBJECT, "Adopción de ${p.nombre}")
                )
            }
        }
        updateSaveIcon(p.esFavorito)
        bindAdoptButton(p)
        findViewById<View>(R.id.bottomBar).visibility = View.VISIBLE
    }

    private fun bindAdoptButton(p: PetDetail) {
        val sol = p.miSolicitud
        val activa = sol != null && sol.estado in listOf("Pendiente", "En revision", "Documentacion pendiente", "Aprobada")
        btnAdoptar.isEnabled = true
        when {
            activa -> {
                btnAdoptar.text = "Ver mi solicitud"
                btnAdoptar.setOnClickListener {
                    startActivity(Intent(this, RequestDetailActivity::class.java).putExtra(Extras.REQUEST_ID, sol!!.idSolicitud))
                }
            }
            p.estadoMascota != "Disponible" -> {
                btnAdoptar.text = "No disponible por ahora"
                btnAdoptar.isEnabled = false
            }
            else -> {
                btnAdoptar.text = "Solicitar adopción de ${p.nombre}"
                btnAdoptar.setOnClickListener {
                    AdoptionRequestSheet(this, p) { creada ->
                        load()
                        startActivity(Intent(this, DocumentationActivity::class.java).putExtra(Extras.REQUEST_ID, creada.idSolicitud)
                            .takeIf { creada.puedeEditarDocumentos }
                            ?: Intent(this, RequestDetailActivity::class.java).putExtra(Extras.REQUEST_ID, creada.idSolicitud))
                    }.show()
                }
            }
        }
    }

    private fun toggleSave() {
        val p = pet ?: return
        val nuevo = !p.esFavorito
        pet = p.copy(esFavorito = nuevo)
        updateSaveIcon(nuevo)
        btnSave.animate().scaleX(0.8f).scaleY(0.8f).setDuration(90).withEndAction {
            btnSave.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(OvershootInterpolator(3f)).start()
        }.start()
        lifecycleScope.launch {
            val r = api { if (nuevo) ApiClient.api.addFavorito(p.idMascota) else ApiClient.api.removeFavorito(p.idMascota) }
            if (r is Outcome.Fail) {
                pet = p
                updateSaveIcon(p.esFavorito)
                findViewById<View>(R.id.detailRoot).showFail(r)
            }
        }
    }

    private fun updateSaveIcon(saved: Boolean) {
        btnSave.setImageResource(if (saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_outline)
        btnSave.contentDescription = if (saved) "Quitar de guardados" else "Guardar"
    }
}
