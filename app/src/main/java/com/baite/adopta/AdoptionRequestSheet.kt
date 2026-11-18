package com.baite.adopta

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.CompoundButton
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.NuevaSolicitud
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.PetDetail
import com.baite.adopta.data.SolicitudDetalle
import com.baite.adopta.data.api
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/**
 * RF020 — Solicitud formal de adopción en 3 pasos. Adoptar es una decisión de años,
 * así que NO se envía con un solo toque. El servidor valida el máximo de 3 solicitudes activas.
 */
class AdoptionRequestSheet(
    private val activity: BaseActivity,
    private val pet: PetDetail,
    private val onCreated: (SolicitudDetalle) -> Unit
) {

    fun show() {
        val dialog = BottomSheetDialog(activity)
        val v = LayoutInflater.from(activity).inflate(R.layout.sheet_adoption_request, null)
        dialog.setContentView(v)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.setBackgroundColor(Color.TRANSPARENT)
            sheet?.let { BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED }
        }

        val tvStep = v.findViewById<TextView>(R.id.tvStepLabel)
        val prog = v.findViewById<LinearProgressIndicator>(R.id.progSteps)
        val steps = listOf<View>(v.findViewById(R.id.step1), v.findViewById(R.id.step2), v.findViewById(R.id.step3))
        val cbs = listOf(R.id.cb1, R.id.cb2, R.id.cb3, R.id.cb4).map { v.findViewById<MaterialCheckBox>(it) }
        val btnAtras = v.findViewById<MaterialButton>(R.id.btnAtras)
        val btnNext = v.findViewById<MaterialButton>(R.id.btnSiguiente)
        val etMotivo = v.findViewById<TextInputEditText>(R.id.etMotivo)
        val etExp = v.findViewById<TextInputEditText>(R.id.etExperiencia)
        val etViv = v.findViewById<TextInputEditText>(R.id.etVivienda)
        val tilMotivo = v.findViewById<TextInputLayout>(R.id.tilMotivo)
        val tilViv = v.findViewById<TextInputLayout>(R.id.tilVivienda)
        v.findViewById<TextView>(R.id.tvResumen).text = "Solicitud para ${pet.nombre} · ${pet.organizacionDetalle.nombre}"

        var step = 1
        var sending = false

        fun render() {
            steps.forEachIndexed { i, s -> s.visible(step == i + 1) }
            tvStep.text = "Paso $step de 3"
            prog.setProgressCompat(step * 100 / 3, true)
            btnAtras.visible(step > 1)
            btnNext.text = if (step == 3) "Enviar solicitud" else "Continuar"
            btnNext.isEnabled = !sending && when (step) {
                1 -> cbs[0].isChecked && cbs[1].isChecked && cbs[2].isChecked
                2 -> cbs[3].isChecked
                else -> true
            }
        }
        val onCheck = CompoundButton.OnCheckedChangeListener { _, _ -> render() }
        cbs.forEach { it.setOnCheckedChangeListener(onCheck) }

        btnAtras.setOnClickListener { if (step > 1) { step--; render() } }
        btnNext.setOnClickListener {
            if (step < 3) {
                step++
                render()
                return@setOnClickListener
            }
            val motivo = etMotivo.text?.toString().orEmpty().trim()
            val viv = etViv.text?.toString().orEmpty().trim()
            tilMotivo.error = if (motivo.length < 10) "Cuéntanos tu motivo (mínimo 10 caracteres)" else null
            tilViv.error = if (viv.length < 10) "Describe tu vivienda (mínimo 10 caracteres)" else null
            if (tilMotivo.error != null || tilViv.error != null) return@setOnClickListener

            sending = true
            render()
            btnNext.text = "Enviando…"
            activity.lifecycleScope.launch {
                val body = NuevaSolicitud(pet.idMascota, motivo, etExp.text?.toString()?.trim()?.ifEmpty { null }, viv, true)
                when (val r = api { ApiClient.api.crearSolicitud(body) }) {
                    is Outcome.Ok -> {
                        dialog.dismiss()
                        onCreated(r.value)
                        return@launch
                    }
                    is Outcome.Fail -> {
                        r.applyTo(mapOf("motivo_adopcion" to tilMotivo, "condiciones_vivienda" to tilViv))
                        v.showFail(r) { btnNext.performClick() }
                    }
                }
                sending = false
                render()
            }
        }
        render()
        dialog.show()
    }
}
