package com.baite.adopta

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import androidx.core.content.ContextCompat

/**
 * Único punto donde se pinta la foto de una mascota.
 * Hoy muestra un placeholder con degradado de la paleta Adopta+ y una huella.
 * Para fotos reales: añade Coil o Glide y, aquí, cárgalas en la vista
 * (cambia scaleType a CENTER_CROP). El degradado queda como placeholder de fondo.
 */
object PetPhoto {

    private val palettes = arrayOf(
        intArrayOf(R.color.adopta_mint, R.color.adopta_teal),
        intArrayOf(R.color.adopta_blue_light, R.color.adopta_teal),
        intArrayOf(R.color.adopta_teal, R.color.adopta_teal_dark),
        intArrayOf(R.color.adopta_cream, R.color.adopta_mint)
    )

    private fun gradient(context: Context, tono: Int): GradientDrawable {
        val p = palettes[Math.floorMod(tono, palettes.size)]
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(ContextCompat.getColor(context, p[0]), ContextCompat.getColor(context, p[1]))
        )
    }

    fun bind(view: ImageView, pet: Pet) {
        view.background = gradient(view.context, pet.tono)
        view.scaleType = ImageView.ScaleType.CENTER
        view.setImageResource(R.drawable.ph_paw)
    }
}
