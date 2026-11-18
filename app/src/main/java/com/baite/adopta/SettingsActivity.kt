package com.baite.adopta

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.Format
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.Preferencias
import com.baite.adopta.data.Session
import com.baite.adopta.data.api
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/** RF005 — Preferencias de búsqueda (en el servidor) y de notificaciones (en este dispositivo). */
class SettingsActivity : BaseActivity() {

    private val sin = "Sin preferencia"
    private val tipos = mapOf(
        R.id.swNotifSolicitudes to "Solicitudes", R.id.swNotifDocumentos to "Documentos", R.id.swNotifSeguimiento to "Seguimiento",
        R.id.swNotifComunidad to "Comunidad", R.id.swNotifDevoluciones to "Devoluciones"
    )

    private fun dd(i: Int) = findViewById<MaterialAutoCompleteTextView>(i)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        tipos.keys.forEach { id ->
            findViewById<MaterialSwitch>(id).apply {
                showText = false
                textOn = ""
                textOff = ""
            }
        }
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        tipos.forEach { (id, tipo) -> findViewById<MaterialSwitch>(id).isChecked = Session.notifEnabled(tipo) }
        dd(R.id.etTamano).setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, listOf(sin) + CatalogFragment.TAMANOS.map { Format.tamano(it) }))
        dd(R.id.etEdad).setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, listOf(sin) + CatalogFragment.RANGOS_EDAD))
        dd(R.id.etEspecie).setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, listOf(sin, "Perro", "Gato")))
        findViewById<MaterialButton>(R.id.btnGuardar).setOnClickListener { save() }
        load()
    }

    private fun load() {
        findViewById<View>(R.id.progressTop).visibility = View.VISIBLE
        lifecycleScope.launch {
            val p = async { api { ApiClient.api.preferencias() } }
            val c = async { api { ApiClient.api.catalogos() } }
            val rp = p.await(); val rc = c.await()
            (rc as? Outcome.Ok)?.let { cat ->
                dd(R.id.etEspecie).setAdapter(ArrayAdapter(this@SettingsActivity, android.R.layout.simple_dropdown_item_1line, listOf(sin) + cat.value.especies))
            }
            when (rp) {
                is Outcome.Ok -> {
                    dd(R.id.etEspecie).setText(rp.value.especie ?: sin, false)
                    dd(R.id.etTamano).setText(rp.value.tamano?.let { Format.tamano(it) } ?: sin, false)
                    dd(R.id.etEdad).setText(rp.value.edadPreferida ?: sin, false)
                }
                is Outcome.Fail -> findViewById<View>(android.R.id.content).showFail(rp) { load() }
            }
            findViewById<View>(R.id.progressTop).visibility = View.INVISIBLE
        }
    }

    private fun save() {
        fun value(i: Int) = dd(i).text?.toString()?.takeIf { it.isNotBlank() && it != sin }
        val body = Preferencias(value(R.id.etEspecie), Format.tamanoDb(value(R.id.etTamano)), value(R.id.etEdad))
        tipos.forEach { (id, tipo) -> Session.setNotifEnabled(tipo, findViewById<MaterialSwitch>(id).isChecked) }
        val btn = findViewById<MaterialButton>(R.id.btnGuardar)
        btn.isEnabled = false
        lifecycleScope.launch {
            when (val r = api { ApiClient.api.updatePreferencias(body) }) {
                is Outcome.Ok -> {
                    android.widget.Toast.makeText(this@SettingsActivity, "Preferencias guardadas", android.widget.Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }
                is Outcome.Fail -> findViewById<View>(android.R.id.content).showFail(r) { save() }
            }
            btn.isEnabled = true
        }
    }
}
