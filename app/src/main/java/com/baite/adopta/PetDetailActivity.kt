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

        bind()

        btnBack.setOnClickListener { finish() }
        btnSave.setOnClickListener {
            SampleData.toggleGuardado(pet.id)
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
        PetPhoto.bind(findViewById<ImageView>(R.id.ivMascota), pet)
        findViewById<TextView>(R.id.tvNombre).text = pet.nombre
        findViewById<TextView>(R.id.tvRaza).text = "${pet.raza} · ${pet.especie}"
        findViewById<TextView>(R.id.tvUbicacion).text =
            "${pet.ubicacion} · " + String.format(Locale.getDefault(), "%.1f km", pet.distanciaKm)
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
        findViewById<TextView>(R.id.tvRespuesta).text = pet.respuesta

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

    private fun updateSaveIcon() {
        val saved = pet.id in SampleData.guardadosIds
        btnSave.setImageResource(if (saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_outline)
        btnSave.contentDescription = if (saved) "Quitar de guardados" else "Guardar"
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
