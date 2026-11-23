package com.baite.adopta

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

/**
 * Único punto donde se pinta la foto de una mascota.
 * Con URL: se carga con Glide (centerCrop) sobre el degradado de la paleta Adopta+.
 * Sin URL (o si falla la descarga): se queda el degradado con la huella.
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

    /** Pinta la foto principal de la mascota. */
    fun bind(view: ImageView, pet: Pet) = bind(view, pet, pet.foto)

    /** Pinta una foto concreta (galería del detalle); [url] null = placeholder. */
    fun bind(view: ImageView, pet: Pet, url: String?) {
        view.background = gradient(view.context, pet.tono)
        if (url.isNullOrBlank()) {
            Glide.with(view).clear(view)
            view.scaleType = ImageView.ScaleType.CENTER
            view.setImageResource(R.drawable.ph_paw)
            return
        }
        view.scaleType = ImageView.ScaleType.CENTER_CROP
        Glide.with(view)
            .load(url)
            .placeholder(R.drawable.ph_paw)
            .error(R.drawable.ph_paw)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .into(view)
    }
}
