package com.baite.adopta

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.Locale

class PetDetailActivity : AppCompatActivity() {

    private lateinit var pet: Pet
    private lateinit var btnSave: ImageButton
    private lateinit var tvFotoContador: TextView
    private var fotoIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pet_detail)

        val found = SampleData.pet(intent.getIntExtra(Extras.PET_ID, -1))
        if (found == null) {
            finish()
            return
        }
        pet = found

        // Foto a sangre bajo la barra de estado: iconos claros y botones desplazados por el inset.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        val btnBack = findViewById<View>(R.id.btnBack)
        btnSave = findViewById(R.id.btnFavorito)
        val bottomBar = findViewById<View>(R.id.bottomBar)
        val scroll = findViewById<View>(R.id.scroll)
        val baseBarPad = bottomBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.detailRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (btnBack.layoutParams as ViewGroup.MarginLayoutParams).topMargin = bars.top + dp(12)
            (btnSave.layoutParams as ViewGroup.MarginLayoutParams).topMargin = bars.top + dp(12)
            btnBack.requestLayout()
            btnSave.requestLayout()
            bottomBar.setPadding(
                bottomBar.paddingLeft, bottomBar.paddingTop, bottomBar.paddingRight, baseBarPad + bars.bottom
            )
            scroll.setPadding(0, 0, 0, dp(88) + bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        tvFotoContador = findViewById(R.id.tvFotoContador)
        bind()

        // Trae la descripción completa y todas las fotos desde la API.
        PetRepository.loadDetail(pet.id) { updated ->
            if (isDestroyed || updated == null) return@loadDetail
            pet = updated
            fotoIndex = 0
            bind()
        }
        // Tocar la foto avanza a la siguiente (si hay más de una).
        findViewById<ImageView>(R.id.ivMascota).setOnClickListener { nextPhoto() }

        btnBack.setOnClickListener { finish() }
        btnSave.setOnClickListener {
            val saved = SampleData.toggleGuardado(pet.id)
            PetRepository.setFavorite(pet.id, saved)
            updateSaveIcon()
            btnSave.animate().scaleX(0.8f).scaleY(0.8f).setDuration(90).withEndAction {
                btnSave.animate().scaleX(1f).scaleY(1f).setDuration(220)
                    .setInterpolator(OvershootInterpolator(3f)).start()
            }.start()
        }
        findViewById<MaterialButton>(R.id.btnAdoptar).apply {
            text = "Quiero conocer a ${pet.nombre}"
            setOnClickListener { AdoptionRequestSheet(this@PetDetailActivity, pet).show() }
        }
    }

    private fun bind() {
        showPhoto()
        findViewById<TextView>(R.id.tvNombre).text = pet.nombre
        findViewById<TextView>(R.id.tvRaza).text = "${pet.raza} · ${pet.especie}"
        findViewById<TextView>(R.id.tvUbicacion).text =
            if (pet.distanciaKm >= 0) {
                "${pet.ubicacion} · " + String.format(Locale.getDefault(), "%.1f km", pet.distanciaKm)
            } else {
                pet.ubicacion
            }
        findViewById<TextView>(R.id.tileEdad).text = pet.edad
        findViewById<TextView>(R.id.tileTamano).text = pet.tamano
        findViewById<TextView>(R.id.tileEnergia).text = pet.energia
        findViewById<TextView>(R.id.tvDescripcion).text =
            pet.descripcion.ifBlank { "${pet.nombre} busca un hogar lleno de cariño." }

        val group = findViewById<ChipGroup>(R.id.cgPersonalidad)
        group.removeAllViews()
        pet.personalidad.forEach { tag ->
            val tv = LayoutInflater.from(this).inflate(R.layout.item_tag, group, false) as TextView
            tv.text = tag
            group.addView(tv)
        }
        val hasTags = pet.personalidad.isNotEmpty()
        group.visibility = if (hasTags) View.VISIBLE else View.GONE
        findViewById<View>(R.id.tvPersonalidadTitulo).visibility = if (hasTags) View.VISIBLE else View.GONE

        findViewById<View>(R.id.chipVacunada).visibility = if (pet.vacunada) View.VISIBLE else View.GONE
        findViewById<View>(R.id.chipEsterilizada).visibility = if (pet.esterilizada) View.VISIBLE else View.GONE

        findViewById<TextView>(R.id.tvRefugio).text = pet.refugio
        findViewById<TextView>(R.id.tvRespuesta).apply {
            text = pet.respuesta
            visibility = if (pet.respuesta.isBlank()) View.GONE else View.VISIBLE
        }

        val card = findViewById<View>(R.id.cardCompat)
        if (pet.compatibilidad > 0) {
            card.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvCompat).text = "${pet.compatibilidad}%"
            findViewById<LinearProgressIndicator>(R.id.progCompat).setProgressCompat(pet.compatibilidad, true)
            findViewById<TextView>(R.id.tvRazonCompat).text = pet.razonCompat
        } else {
            card.visibility = View.GONE
        }
        updateSaveIcon()
    }

    private fun showPhoto() {
        val iv = findViewById<ImageView>(R.id.ivMascota)
        val total = pet.fotos.size
        if (total == 0) {
            PetPhoto.bind(iv, pet)
            tvFotoContador.visibility = View.GONE
            return
        }
        if (fotoIndex >= total) fotoIndex = 0
        PetPhoto.bind(iv, pet, pet.fotos[fotoIndex])
        tvFotoContador.text = "${fotoIndex + 1}/$total"
        tvFotoContador.visibility = if (total > 1) View.VISIBLE else View.GONE
    }

    private fun nextPhoto() {
        if (pet.fotos.size < 2) return
        fotoIndex = (fotoIndex + 1) % pet.fotos.size
        showPhoto()
    }

    private fun updateSaveIcon() {
        val saved = pet.id in SampleData.guardadosIds
        btnSave.setImageResource(if (saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_outline)
        btnSave.contentDescription = if (saved) "Quitar de guardados" else "Guardar"
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
