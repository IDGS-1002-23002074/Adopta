package com.baite.adopta

import android.app.Activity
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * Solicitud formal de adopción en 3 pasos. Adoptar es una decisión de años,
 * así que NO se envía con un solo toque.
 * TODO backend: validar perfil del adoptante, enfriamiento de 24 h y límite de solicitudes.
 */
class AdoptionRequestSheet(private val activity: Activity, private val pet: Pet) {

    fun show() {
        if (pet.id in SampleData.solicitudes) {
            Toast.makeText(activity, "Ya enviaste una solicitud para ${pet.nombre}", Toast.LENGTH_SHORT).show()
            return
        }
        if (SampleData.solicitudes.size >= SampleData.MAX_SOLICITUDES) {
            Toast.makeText(
                activity,
                "Tienes ${SampleData.MAX_SOLICITUDES} solicitudes activas. Espera una respuesta antes de enviar otra.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val dialog = BottomSheetDialog(activity)
        val v = LayoutInflater.from(activity).inflate(R.layout.sheet_adoption_request, null)
        dialog.setContentView(v)
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.setBackgroundColor(Color.TRANSPARENT)
            sheet?.let { BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED }
        }

        val tvStep = v.findViewById<TextView>(R.id.tvStepLabel)
        val prog = v.findViewById<LinearProgressIndicator>(R.id.progSteps)
        val step1 = v.findViewById<View>(R.id.step1)
        val step2 = v.findViewById<View>(R.id.step2)
        val step3 = v.findViewById<View>(R.id.step3)
        val cb1 = v.findViewById<MaterialCheckBox>(R.id.cb1)
        val cb2 = v.findViewById<MaterialCheckBox>(R.id.cb2)
        val cb3 = v.findViewById<MaterialCheckBox>(R.id.cb3)
        val cb4 = v.findViewById<MaterialCheckBox>(R.id.cb4)
        val btnAtras = v.findViewById<MaterialButton>(R.id.btnAtras)
        val btnNext = v.findViewById<MaterialButton>(R.id.btnSiguiente)
        v.findViewById<TextView>(R.id.tvResumen).text = "Solicitud para ${pet.nombre} · ${pet.refugio}"

        var step = 1

        fun render() {
            step1.visibility = if (step == 1) View.VISIBLE else View.GONE
            step2.visibility = if (step == 2) View.VISIBLE else View.GONE
            step3.visibility = if (step == 3) View.VISIBLE else View.GONE
            tvStep.text = "Paso $step de 3"
            prog.setProgressCompat(step * 100 / 3, true)
            btnAtras.visibility = if (step == 1) View.GONE else View.VISIBLE
            btnNext.text = if (step == 3) "Enviar solicitud" else "Continuar"
            btnNext.isEnabled = when (step) {
                1 -> cb1.isChecked && cb2.isChecked && cb3.isChecked
                2 -> cb4.isChecked
                else -> true
            }
        }

        val onCheck = CompoundButton.OnCheckedChangeListener { _, _ -> render() }
        cb1.setOnCheckedChangeListener(onCheck)
        cb2.setOnCheckedChangeListener(onCheck)
        cb3.setOnCheckedChangeListener(onCheck)
        cb4.setOnCheckedChangeListener(onCheck)

        btnAtras.setOnClickListener {
            if (step > 1) {
                step--
                render()
            }
        }
        btnNext.setOnClickListener {
            if (step < 3) {
                step++
                render()
            } else {
                // TODO: enviar la solicitud al backend.
                SampleData.solicitudes.add(pet.id)
                dialog.dismiss()
                Toast.makeText(
                    activity,
                    "Solicitud enviada. Te avisaremos cuando ${pet.refugio} responda.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        render()
        dialog.show()
    }
}
