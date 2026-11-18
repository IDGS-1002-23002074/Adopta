package com.baite.adopta

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.core.widget.NestedScrollView
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.InfoHogar
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.SolicitudDetalle
import com.baite.adopta.data.api
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/** Módulo 5 — Información complementaria del hogar. "Guardar borrador" o "Guardar y continuar" (Enviado). */
class HouseholdFormActivity : BaseActivity() {

    /** valor de la BD -> texto que ve la persona */
    private val regimen = listOf("Propia" to "Propia", "Rentada" to "Rentada", "Familiar" to "Familiar", "Otro" to "Otro")
    private val horas = listOf("Menos de 4 horas" to "Menos de 4 horas", "De 4 a 8 horas" to "De 4 a 8 horas", "Mas de 8 horas" to "Más de 8 horas")
    private val lugares = listOf(
        "Dentro de casa" to "Dentro de casa", "Patio o exterior" to "Patio o exterior",
        "Area techada exterior" to "Área techada exterior", "Otro" to "Otro"
    )
    private val presupuestos = listOf(
        "Menos de $500" to "Menos de \$500", "$500 a $1,000" to "\$500 a \$1,000",
        "$1,001 a $2,000" to "\$1,001 a \$2,000", "Mas de $2,000" to "Más de \$2,000"
    )

    private var id = -1
    private var saving = false

    private fun dd(i: Int) = findViewById<MaterialAutoCompleteTextView>(i)
    private fun sw(i: Int) = findViewById<MaterialSwitch>(i)
    private fun et(i: Int) = findViewById<TextInputEditText>(i)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_household_form)
        id = intent.getIntExtra(Extras.REQUEST_ID, -1)
        if (id < 0) { finish(); return }
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        fun setup(view: MaterialAutoCompleteTextView, options: List<Pair<String, String>>) {
            view.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, options.map { it.second }))
        }
        setup(dd(R.id.etRegimen), regimen)
        setup(dd(R.id.etHorasSolo), horas)
        setup(dd(R.id.etLugarDormir), lugares)
        setup(dd(R.id.etPresupuesto), presupuestos)

        fun toggles() {
            findViewById<View>(R.id.llArrendador).visible(valueOf(dd(R.id.etRegimen), regimen) == "Rentada")
            findViewById<View>(R.id.llCercado).visible(sw(R.id.swExterior).isChecked)
            findViewById<View>(R.id.llOtras).visible(sw(R.id.swOtras).isChecked)
        }
        dd(R.id.etRegimen).setOnItemClickListener { _, _, _, _ -> toggles() }
        sw(R.id.swExterior).setOnCheckedChangeListener { _, _ -> toggles() }
        sw(R.id.swOtras).setOnCheckedChangeListener { _, _ -> toggles() }

        findViewById<MaterialButton>(R.id.btnGuardar).setOnClickListener { save("Enviado") }
        findViewById<MaterialButton>(R.id.btnBorrador).setOnClickListener { save("Borrador") }
        load { toggles() }
    }

    private fun valueOf(view: MaterialAutoCompleteTextView, options: List<Pair<String, String>>): String? {
        val shown = view.text?.toString().orEmpty()
        return options.firstOrNull { it.second == shown }?.first
    }

    private fun showValue(view: MaterialAutoCompleteTextView, options: List<Pair<String, String>>, value: String?) {
        view.setText(options.firstOrNull { it.first == value }?.second.orEmpty(), false)
    }

    private fun load(done: () -> Unit) {
        val bar = findViewById<View>(R.id.toolbar)
        lifecycleScope.launch {
            when (val r = api { ApiClient.api.solicitud(id) }) {
                is Outcome.Ok -> {
                    if (!r.value.puedeEditarDocumentos) {
                        findViewById<View>(android.R.id.content).snack("Esta solicitud ya no admite cambios")
                        findViewById<MaterialButton>(R.id.btnGuardar).isEnabled = false
                        findViewById<MaterialButton>(R.id.btnBorrador).isEnabled = false
                    }
                    r.value.informacion?.let { fill(it) }
                    done()
                }
                is Outcome.Fail -> bar.showFail(r) { load(done) }
            }
        }
    }

    private fun fill(i: InfoHogar) {
        showValue(dd(R.id.etRegimen), regimen, i.regimenVivienda)
        showValue(dd(R.id.etHorasSolo), horas, i.horasSoloDia)
        showValue(dd(R.id.etLugarDormir), lugares, i.lugarDormir)
        showValue(dd(R.id.etPresupuesto), presupuestos, i.presupuestoMensual)
        sw(R.id.swArrendador).isChecked = i.arrendadorPermiteMascotas == true
        sw(R.id.swExterior).isChecked = i.tieneEspacioExterior == true
        sw(R.id.swCercado).isChecked = i.espacioExteriorCercado == true
        et(R.id.etSuperficie).setText(i.superficieM2?.toString().orEmpty())
        et(R.id.etPersonas).setText(i.numPersonasHogar?.toString().orEmpty())
        sw(R.id.swMenores).isChecked = i.hayMenores == true
        sw(R.id.swAcuerdo).isChecked = i.hogarDeAcuerdo == true
        et(R.id.etCuidador).setText(i.cuidadorAlterno.orEmpty())
        et(R.id.etVetNombre).setText(i.veterinarioNombre.orEmpty())
        et(R.id.etVetTelefono).setText(i.veterinarioTelefono.orEmpty())
        sw(R.id.swOtras).isChecked = i.tieneOtrasMascotas == true
        et(R.id.etCantidadOtras).setText(i.cantidadOtrasMascotas?.toString().orEmpty())
        et(R.id.etEspeciesOtras).setText(i.especiesOtrasMascotas.orEmpty())
        sw(R.id.swOtrasEster).isChecked = i.otrasEsterilizadas == true
        sw(R.id.swOtrasVac).isChecked = i.otrasVacunadas == true
        et(R.id.etHistorial).setText(i.historialMascotasPrevias.orEmpty())
        sw(R.id.swVisita).isChecked = i.aceptaVisitaDomiciliaria == true
        sw(R.id.swSeguimiento).isChecked = i.aceptaSeguimiento == true
        sw(R.id.swCompromisoEster).isChecked = i.compromisoEsterilizacion == true
        sw(R.id.swPrivacidad).isChecked = i.aceptaAvisoPrivacidad == true
        sw(R.id.swVeracidad).isChecked = i.declaracionVeracidad == true
        et(R.id.etFirma).setText(i.firmaNombre.orEmpty())
    }

    private fun txt(i: Int): String? = et(i).text?.toString()?.trim()?.ifEmpty { null }

    private fun build(estado: String): InfoHogar {
        val otras = sw(R.id.swOtras).isChecked
        val rentada = valueOf(dd(R.id.etRegimen), regimen) == "Rentada"
        val exterior = sw(R.id.swExterior).isChecked
        return InfoHogar(
            regimenVivienda = valueOf(dd(R.id.etRegimen), regimen),
            arrendadorPermiteMascotas = if (rentada) sw(R.id.swArrendador).isChecked else null,
            tieneEspacioExterior = exterior,
            espacioExteriorCercado = if (exterior) sw(R.id.swCercado).isChecked else null,
            superficieM2 = txt(R.id.etSuperficie)?.toIntOrNull(),
            numPersonasHogar = txt(R.id.etPersonas)?.toIntOrNull(),
            hayMenores = sw(R.id.swMenores).isChecked,
            hogarDeAcuerdo = sw(R.id.swAcuerdo).isChecked,
            horasSoloDia = valueOf(dd(R.id.etHorasSolo), horas),
            lugarDormir = valueOf(dd(R.id.etLugarDormir), lugares),
            cuidadorAlterno = txt(R.id.etCuidador),
            presupuestoMensual = valueOf(dd(R.id.etPresupuesto), presupuestos),
            tieneOtrasMascotas = otras,
            cantidadOtrasMascotas = if (otras) txt(R.id.etCantidadOtras)?.toIntOrNull() else null,
            especiesOtrasMascotas = if (otras) txt(R.id.etEspeciesOtras) else null,
            otrasEsterilizadas = if (otras) sw(R.id.swOtrasEster).isChecked else null,
            otrasVacunadas = if (otras) sw(R.id.swOtrasVac).isChecked else null,
            historialMascotasPrevias = txt(R.id.etHistorial),
            veterinarioNombre = txt(R.id.etVetNombre),
            veterinarioTelefono = txt(R.id.etVetTelefono),
            aceptaVisitaDomiciliaria = sw(R.id.swVisita).isChecked,
            aceptaSeguimiento = sw(R.id.swSeguimiento).isChecked,
            compromisoEsterilizacion = sw(R.id.swCompromisoEster).isChecked,
            aceptaAvisoPrivacidad = sw(R.id.swPrivacidad).isChecked,
            declaracionVeracidad = sw(R.id.swVeracidad).isChecked,
            firmaNombre = txt(R.id.etFirma),
            estadoFormulario = estado
        )
    }

    private fun save(estado: String) {
        if (saving) return
        val body = build(estado)
        val errores = mutableListOf<String>()
        if (estado == "Enviado") {
            if (body.regimenVivienda == null) errores += "Selecciona el régimen de tu vivienda"
            if (body.horasSoloDia == null) errores += "Indica cuánto tiempo estaría solo al día"
            if (body.lugarDormir == null) errores += "Indica dónde dormiría"
            if (body.presupuestoMensual == null) errores += "Indica tu presupuesto mensual"
            if ((body.numPersonasHogar ?: 0) < 1) errores += "Indica cuántas personas viven en el hogar"
        }
        if (errores.isNotEmpty()) { showErrors(errores); return }
        showErrors(emptyList())

        saving = true
        val btns = listOf(R.id.btnGuardar, R.id.btnBorrador).map { findViewById<MaterialButton>(it) }
        btns.forEach { it.isEnabled = false }
        findViewById<View>(R.id.progressTop)?.visibility = View.VISIBLE
        lifecycleScope.launch {
            when (val r = api { ApiClient.api.guardarInformacion(id, body) }) {
                is Outcome.Ok -> {
                    android.widget.Toast.makeText(
                        this@HouseholdFormActivity,
                        if (estado == "Enviado") "Información guardada" else "Borrador guardado",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    finish()
                    return@launch
                }
                is Outcome.Fail -> {
                    if (r.fields.isNotEmpty()) showErrors(r.fields.values.toList()) else findViewById<View>(android.R.id.content).showFail(r) { save(estado) }
                }
            }
            saving = false
            btns.forEach { it.isEnabled = true }
            findViewById<View>(R.id.progressTop)?.visibility = View.INVISIBLE
        }
    }

    private fun showErrors(list: List<String>) {
        val card = findViewById<View>(R.id.cardErrores)
        card.visible(list.isNotEmpty())
        if (list.isNotEmpty()) {
            findViewById<TextView>(R.id.tvErrores).text = "Revisa lo siguiente:\n" + list.joinToString("\n") { "• $it" }
            findViewById<NestedScrollView>(R.id.scroll).smoothScrollTo(0, 0)
        }
    }
}
