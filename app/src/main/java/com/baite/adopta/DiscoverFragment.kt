package com.baite.adopta

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import java.util.Locale
import kotlin.math.abs

/**
 * Pantalla "Descubrir": pila de tarjetas con swipe.
 *  → derecha  = "me interesa" (bajo compromiso, reversible)
 *  ← izquierda = pasar
 *  ↑ o tap    = abrir detalle
 * El botón de marcador guarda sin avisar al refugio.
 */
class DiscoverFragment : Fragment(R.layout.fragment_discover) {

    private lateinit var stack: FrameLayout
    private lateinit var tvContador: TextView
    private lateinit var layoutDone: View
    private lateinit var actions: View
    private lateinit var matchOverlay: View

    private val deck = mutableListOf<Pet>()
    private var busy = false
    private var lastPet: Pet? = null
    private var lastKind = KIND_PASS

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        stack = view.findViewById(R.id.cardStack)
        tvContador = view.findViewById(R.id.tvContador)
        layoutDone = view.findViewById(R.id.layoutDone)
        actions = view.findViewById(R.id.actions)
        matchOverlay = view.findViewById(R.id.matchOverlay)

        val btnPass = view.findViewById<View>(R.id.btnPass)
        val btnLike = view.findViewById<View>(R.id.btnLike)
        val btnSave = view.findViewById<View>(R.id.btnSave)
        btnPass.setOnClickListener { bounce(btnPass); swipeTop(false) }
        btnLike.setOnClickListener { bounce(btnLike); swipeTop(true) }
        btnSave.setOnClickListener { bounce(btnSave); saveTop() }

        view.findViewById<View>(R.id.btnSeguir).setOnClickListener { hideMatch() }
        view.findViewById<View>(R.id.btnEscribir).setOnClickListener {
            hideMatch()
            // TODO: abrir el chat con el refugio cuando exista el backend.
            (activity as? HomeActivity)?.selectTab(R.id.nav_notificaciones)
        }
        view.findViewById<View>(R.id.btnVerGuardados).setOnClickListener {
            (activity as? HomeActivity)?.selectTab(R.id.nav_favoritos)
        }

        deck.clear()
        deck.addAll(SampleData.deck())
        render()
    }

    override fun onResume() {
        super.onResume()
        syncDeck()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) syncDeck()
    }

    /** Si cambió el estado fuera de esta pantalla (detalle, guardados), reconstruye el mazo. */
    private fun syncDeck() {
        if (view == null || busy) return
        val fresh = SampleData.deck()
        if (fresh.map { it.id } != deck.map { it.id }) {
            deck.clear()
            deck.addAll(fresh)
            render()
        }
    }

    // ---------------------------------------------------------------- render

    private fun render() {
        stack.removeAllViews()
        val hasCards = deck.isNotEmpty()
        layoutDone.visibility = if (hasCards) View.GONE else View.VISIBLE
        actions.visibility = if (hasCards) View.VISIBLE else View.INVISIBLE
        tvContador.text = if (hasCards) {
            "${deck.size} ${if (deck.size == 1) "mascota" else "mascotas"} para ti hoy"
        } else {
            "Tu selección de hoy terminó"
        }
        val n = minOf(3, deck.size)
        for (i in n - 1 downTo 0) {
            val card = layoutInflater.inflate(R.layout.item_pet_card, stack, false)
            bindCard(card, deck[i])
            card.scaleX = 1f - 0.07f * i
            card.translationY = dp(14f) * i
            stack.addView(card)
        }
        if (hasCards) attachTouch(stack.getChildAt(stack.childCount - 1), deck[0])
    }

    private fun bindCard(card: View, pet: Pet) {
        PetPhoto.bind(card.findViewById<ImageView>(R.id.ivMascota), pet)
        card.findViewById<TextView>(R.id.tvNombre).text = pet.nombre
        card.findViewById<TextView>(R.id.tvDetalle).text = "${pet.raza} · ${pet.edad}"
        card.findViewById<TextView>(R.id.tvDistancia).text =
            String.format(Locale.getDefault(), "%.1f km", pet.distanciaKm)
    }

    // ---------------------------------------------------------------- gestos

    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouch(card: View, pet: Pet) {
        val slop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        val threshold = dp(110f)
        var downX = 0f
        var downY = 0f
        var dragging = false
        var tracker: VelocityTracker? = null

        card.setOnTouchListener { v, e ->
            if (busy) return@setOnTouchListener true
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    dragging = false
                    tracker?.recycle()
                    tracker = VelocityTracker.obtain().also { it.addMovement(e) }
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    tracker?.addMovement(e)
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        v.translationX = dx
                        v.translationY = dy * 0.4f
                        v.rotation = (dx / v.width.coerceAtLeast(1)) * 14f
                        updateStamps(v, dx / threshold)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tracker?.addMovement(e)
                    tracker?.computeCurrentVelocity(1000)
                    val vx = tracker?.xVelocity ?: 0f
                    tracker?.recycle()
                    tracker = null
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    val up = e.actionMasked == MotionEvent.ACTION_UP
                    when {
                        !dragging && up -> {
                            v.performClick()
                            openDetail(pet)
                        }
                        dx > threshold || (vx > 1200f && dx > dp(30f)) -> flyOut(v, true)
                        dx < -threshold || (vx < -1200f && dx < -dp(30f)) -> flyOut(v, false)
                        dy < -threshold * 1.3f && abs(dx) < threshold -> {
                            snapBack(v)
                            openDetail(pet)
                        }
                        else -> snapBack(v)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun updateStamps(card: View, progress: Float) {
        val like = progress.coerceIn(0f, 1f)
        val pass = (-progress).coerceIn(0f, 1f)
        card.findViewById<View>(R.id.stampLike).apply {
            alpha = like
            scaleX = 0.6f + 0.5f * like
            scaleY = 0.6f + 0.5f * like
        }
        card.findViewById<View>(R.id.stampPass).apply {
            alpha = pass
            scaleX = 0.6f + 0.5f * pass
            scaleY = 0.6f + 0.5f * pass
        }
        card.findViewById<View>(R.id.tintLike).alpha = like * 0.45f
        card.findViewById<View>(R.id.tintPass).alpha = pass * 0.45f
    }

    private fun snapBack(v: View) {
        updateStamps(v, 0f)
        v.animate()
            .translationX(0f).translationY(0f).rotation(0f)
            .setDuration(280)
            .setInterpolator(OvershootInterpolator(1.2f))
            .start()
    }

    private fun advanceStack() {
        val n = stack.childCount
        if (n >= 2) {
            stack.getChildAt(n - 2).animate().scaleX(1f).translationY(0f).setDuration(260).start()
        }
        if (n >= 3) {
            stack.getChildAt(n - 3).animate().scaleX(1f - 0.07f).translationY(dp(14f)).setDuration(260).start()
        }
    }

    private fun swipeTop(liked: Boolean) {
        if (busy || deck.isEmpty() || stack.childCount == 0) return
        val top = stack.getChildAt(stack.childCount - 1)
        flyOut(top, liked)
    }

    private fun flyOut(v: View, liked: Boolean) {
        if (busy) return
        busy = true
        v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        val dir = if (liked) 1f else -1f
        updateStamps(v, dir)
        advanceStack()
        v.animate()
            .translationX(dir * v.width * 1.5f)
            .translationY(v.translationY + dp(40f))
            .rotation(dir * 22f)
            .alpha(0f)
            .setDuration(260)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { onDecided(if (liked) KIND_LIKE else KIND_PASS) }
            .start()
    }

    private fun saveTop() {
        if (busy || deck.isEmpty() || stack.childCount == 0) return
        busy = true
        val top = stack.getChildAt(stack.childCount - 1)
        top.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        advanceStack()
        top.animate()
            .translationY(-top.height * 1.1f)
            .alpha(0f)
            .setDuration(280)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { onDecided(KIND_SAVE) }
            .start()
    }

    private fun onDecided(kind: Int) {
        if (view == null) return
        if (deck.isEmpty()) {
            busy = false
            return
        }
        val pet = deck.removeAt(0)
        when (kind) {
            KIND_LIKE -> {
                SampleData.decididos.add(pet.id)
                SampleData.intereses.add(pet.id)
            }
            KIND_SAVE -> SampleData.guardadosIds.add(pet.id)
            else -> SampleData.decididos.add(pet.id)
        }
        lastPet = pet
        lastKind = kind
        busy = false
        render()
        if (kind == KIND_LIKE && pet.aceptaInteres) showMatch(pet) else showUndo(pet, kind)
    }

    // ---------------------------------------------------------------- deshacer

    private fun showUndo(pet: Pet, kind: Int) {
        val v = view ?: return
        val msg = when (kind) {
            KIND_LIKE -> "Enviaste tu interés a ${pet.nombre}"
            KIND_SAVE -> "${pet.nombre} está en tus guardados"
            else -> "Pasaste a ${pet.nombre}"
        }
        Snackbar.make(v, msg, Snackbar.LENGTH_LONG)
            .setAction("Deshacer") { undo() }
            .setAnchorView(actions)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.adopta_text))
            .setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            .setActionTextColor(ContextCompat.getColor(requireContext(), R.color.adopta_mint))
            .show()
    }

    private fun undo() {
        val p = lastPet ?: return
        when (lastKind) {
            KIND_SAVE -> SampleData.guardadosIds.remove(p.id)
            KIND_LIKE -> {
                SampleData.intereses.remove(p.id)
                SampleData.decididos.remove(p.id)
            }
            else -> SampleData.decididos.remove(p.id)
        }
        lastPet = null
        deck.add(0, p)
        render()
    }

    // ---------------------------------------------------------------- match

    private fun showMatch(pet: Pet) {
        val root = view ?: return
        PetPhoto.bind(root.findViewById<ImageView>(R.id.matchPetPhoto), pet)
        root.findViewById<TextView>(R.id.tvMatchTitle).text = "Es un match con ${pet.nombre}"
        root.findViewById<TextView>(R.id.tvMatchSub).text =
            "${pet.refugio} aceptó tu interés. Ya pueden escribirse para conocerse."

        val user = root.findViewById<View>(R.id.matchUser)
        val petCard = root.findViewById<View>(R.id.matchPetCard)
        val title = root.findViewById<View>(R.id.tvMatchTitle)
        val sub = root.findViewById<View>(R.id.tvMatchSub)

        matchOverlay.alpha = 0f
        matchOverlay.visibility = View.VISIBLE
        matchOverlay.animate().alpha(1f).setDuration(220).start()

        user.translationX = -dp(160f)
        petCard.translationX = dp(160f)
        title.alpha = 0f
        title.translationY = dp(16f)
        sub.alpha = 0f
        val spring = OvershootInterpolator(1.4f)
        user.animate().translationX(0f).setStartDelay(120).setDuration(520).setInterpolator(spring).start()
        petCard.animate().translationX(0f).setStartDelay(120).setDuration(520).setInterpolator(spring).start()
        title.animate().alpha(1f).translationY(0f).setStartDelay(380).setDuration(320).start()
        sub.animate().alpha(1f).setStartDelay(480).setDuration(320).start()

        confirmHaptic(matchOverlay)
    }

    private fun hideMatch() {
        matchOverlay.animate().alpha(0f).setDuration(180).withEndAction {
            matchOverlay.visibility = View.GONE
        }.start()
    }

    // ---------------------------------------------------------------- utilidades

    private fun openDetail(pet: Pet) {
        startActivity(
            Intent(requireContext(), PetDetailActivity::class.java)
                .putExtra(Extras.PET_ID, pet.id)
        )
    }

    private fun bounce(v: View) {
        v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(90).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(200)
                .setInterpolator(OvershootInterpolator(3f)).start()
        }.start()
    }

    private fun confirmHaptic(v: View) {
        val c = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.LONG_PRESS
        v.performHapticFeedback(c)
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    private companion object {
        const val KIND_PASS = 0
        const val KIND_LIKE = 1
        const val KIND_SAVE = 2
    }
}
