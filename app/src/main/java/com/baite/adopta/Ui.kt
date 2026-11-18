package com.baite.adopta

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import coil.load
import com.baite.adopta.data.Format
import com.baite.adopta.data.Outcome
import com.google.android.material.snackbar.Snackbar

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun View.dp(v: Int): Int = context.dp(v)

fun View.snack(message: String) {
    Snackbar.make(this, message, Snackbar.LENGTH_LONG).show()
}

/** Muestra el error de una llamada; si hay conexión caída ofrece "Reintentar" (RNF014). */
fun View.showFail(fail: Outcome.Fail, retry: (() -> Unit)? = null) {
    val s = Snackbar.make(this, fail.message, Snackbar.LENGTH_LONG)
    if (retry != null && (fail.offline || fail.status >= 500)) s.setAction("Reintentar") { retry() }
    s.show()
}

fun View.visible(show: Boolean) {
    visibility = if (show) View.VISIBLE else View.GONE
}

private val tonos = arrayOf(
    intArrayOf(R.color.adopta_mint, R.color.adopta_teal),
    intArrayOf(R.color.adopta_blue_light, R.color.adopta_teal),
    intArrayOf(R.color.adopta_teal, R.color.adopta_teal_dark),
    intArrayOf(R.color.adopta_cream, R.color.adopta_mint)
)

private fun gradiente(ctx: Context, tono: Int) = GradientDrawable(
    GradientDrawable.Orientation.TL_BR,
    tonos[Math.floorMod(tono, tonos.size)].map { ContextCompat.getColor(ctx, it) }.toIntArray()
)

/** Foto de mascota o publicación: si no hay URL se pinta un degradado con la huella. */
fun ImageView.loadPhoto(url: String?, tono: Int = 0) {
    background = gradiente(context, tono)
    if (url.isNullOrBlank()) {
        scaleType = ImageView.ScaleType.CENTER
        setImageResource(R.drawable.ph_paw)
    } else {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setImageDrawable(null)
        load(url) { crossfade(true) }
    }
}

/** Logo/avatar circular (usar con ShapeableImageView + Adopta.Circle). */
fun ImageView.loadAvatar(url: String?) {
    val pad = dp(5)
    if (url.isNullOrBlank()) {
        setBackgroundResource(R.color.adopta_blue_light)
        setPadding(pad, pad, pad, pad)
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageResource(R.drawable.ic_pets)
        setColorFilter(ContextCompat.getColor(context, R.color.adopta_teal_dark))
    } else {
        clearColorFilter()
        setPadding(0, 0, 0, 0)
        setBackgroundResource(R.color.white)
        scaleType = ImageView.ScaleType.CENTER_CROP
        load(url) { crossfade(true) }
    }
}

/** Chip de estado con el color según el estado (verde, ámbar, rojo o neutro). */
fun TextView.applyStatus(estado: String?) = applyTone(Format.estado(estado), Format.tone(estado))

private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")
fun isEmail(v: String) = EMAIL.matches(v.trim())
fun passwordError(v: String): String? = when {
    v.length < 8 -> "Mínimo 8 caracteres"
    !v.any { it.isLetter() } || !v.any { it.isDigit() } -> "Debe incluir letras y números"
    else -> null
}

/** Pinta los errores por campo que devolvió el servidor en los TextInputLayout correspondientes. */
fun Outcome.Fail.applyTo(map: Map<String, com.google.android.material.textfield.TextInputLayout>) {
    fields.forEach { (k, v) -> map[k]?.error = v }
}

/** Chip con texto propio y color según el tono. */
fun TextView.applyTone(label: String, tone: Format.Tone) {
    text = label
    val (bg, fg) = when (tone) {
        Format.Tone.GOOD -> R.drawable.bg_status_green to R.color.adopta_success
        Format.Tone.WARN -> R.drawable.bg_status_amber to R.color.adopta_warning
        Format.Tone.BAD -> R.drawable.bg_status_red to R.color.adopta_danger
        Format.Tone.NEUTRAL -> R.drawable.bg_status_gray to R.color.adopta_teal_dark
    }
    setBackgroundResource(bg)
    setTextColor(ContextCompat.getColor(context, fg))
}
