package com.baite.adopta

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.Format
import com.baite.adopta.data.MeResponse
import com.baite.adopta.data.MeUpdate
import com.baite.adopta.data.Media
import com.baite.adopta.data.MediaException
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.Session
import com.baite.adopta.data.api
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.util.Calendar

/** RF004 — Consultar y actualizar datos personales, de contacto y foto de perfil. */
class EditProfileActivity : BaseActivity() {

    private val viviendas = listOf("Casa", "Departamento", "Casa compartida", "Otro")
    private var fechaNac: String? = null
    private lateinit var me: MeResponse

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { uploadPhoto(it) } }

    private fun et(i: Int) = findViewById<TextInputEditText>(i)
    private fun txt(i: Int) = et(i).text?.toString()?.trim()?.ifEmpty { null }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)
        findViewById<MaterialSwitch>(R.id.swTieneMascotas).apply {
            showText = false
            textOn = ""
            textOff = ""
        }
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<MaterialAutoCompleteTextView>(R.id.etTipoVivienda)
            .setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, viviendas))
        et(R.id.etFechaNac).apply {
            inputType = InputType.TYPE_NULL
            isFocusable = false
            setOnClickListener { pickDate() }
        }
        findViewById<View>(R.id.btnFoto).setOnClickListener { pickPhoto.launch("image/*") }
        findViewById<MaterialButton>(R.id.btnGuardar).setOnClickListener { save() }
        load()
    }

    private fun load() {
        findViewById<View>(R.id.progressTop).visibility = View.VISIBLE
        lifecycleScope.launch {
            when (val r = api { ApiClient.api.me() }) {
                is Outcome.Ok -> { me = r.value; fill(r.value) }
                is Outcome.Fail -> findViewById<View>(android.R.id.content).showFail(r) { load() }
            }
            findViewById<View>(R.id.progressTop).visibility = View.INVISIBLE
        }
    }

    private fun fill(m: MeResponse) {
        val u = m.usuario; val p = m.perfil
        et(R.id.etNombre).setText(u.nombre)
        et(R.id.etPaterno).setText(u.apellidoPaterno)
        et(R.id.etMaterno).setText(u.apellidoMaterno.orEmpty())
        et(R.id.etTelefono).setText(u.telefono.orEmpty())
        fechaNac = p.fechaNacimiento?.take(10)
        et(R.id.etFechaNac).setText(fechaNac?.let { Format.date(it) }.orEmpty())
        et(R.id.etDireccion).setText(p.direccion.orEmpty())
        et(R.id.etCiudad).setText(p.ciudad.orEmpty())
        et(R.id.etCP).setText(p.codigoPostal.orEmpty())
        findViewById<MaterialAutoCompleteTextView>(R.id.etTipoVivienda).setText(p.tipoVivienda.orEmpty(), false)
        findViewById<MaterialSwitch>(R.id.swTieneMascotas).isChecked = p.tieneMascotas
        et(R.id.etExperiencia).setText(p.experienciaMascotas.orEmpty())
        et(R.id.etOcupacion).setText(p.ocupacion.orEmpty())
        et(R.id.etEmNombre).setText(p.contactoEmergenciaNombre.orEmpty())
        et(R.id.etEmTelefono).setText(p.contactoEmergenciaTelefono.orEmpty())
        et(R.id.etEmParentesco).setText(p.contactoEmergenciaParentesco.orEmpty())
        val iv = findViewById<ImageView>(R.id.ivAvatar)
        val tv = findViewById<TextView>(R.id.tvIniciales)
        if (u.fotoPerfil.isNullOrBlank()) {
            iv.setBackgroundResource(R.color.adopta_blue_light)
            tv.visible(true)
            tv.text = u.iniciales
        } else {
            tv.visible(false)
            iv.loadAvatar(u.fotoPerfil)
        }
    }

    private fun pickDate() {
        val c = Calendar.getInstance()
        fechaNac?.split("-")?.takeIf { it.size == 3 }?.let { c.set(it[0].toInt(), it[1].toInt() - 1, it[2].toInt()) }
        val dlg = DatePickerDialog(this, { _, y, m, d ->
            fechaNac = Format.iso(y, m, d)
            et(R.id.etFechaNac).setText(Format.date(fechaNac))
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
        dlg.datePicker.maxDate = System.currentTimeMillis()
        dlg.show()
    }

    private fun uploadPhoto(uri: Uri) {
        findViewById<View>(R.id.progressTop).visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val part = Media.imagePart(this@EditProfileActivity, uri, "foto")
                when (val r = api { ApiClient.api.uploadFoto(part) }) {
                    is Outcome.Ok -> {
                        findViewById<TextView>(R.id.tvIniciales).visible(false)
                        findViewById<ImageView>(R.id.ivAvatar).loadAvatar(r.value.fotoPerfil)
                        Session.user?.let { Session.updateUser(it.copy(fotoPerfil = r.value.fotoPerfil)) }
                    }
                    is Outcome.Fail -> findViewById<View>(android.R.id.content).showFail(r)
                }
            } catch (e: MediaException) {
                findViewById<View>(android.R.id.content).snack(e.message ?: "Foto no válida")
            } catch (e: Exception) {
                findViewById<View>(android.R.id.content).snack("No se pudo leer la foto")
            }
            findViewById<View>(R.id.progressTop).visibility = View.INVISIBLE
        }
    }

    private fun save() {
        val tilNombre = findViewById<TextInputLayout>(R.id.tilNombre)
        val tilPaterno = findViewById<TextInputLayout>(R.id.tilPaterno)
        val tilTel = findViewById<TextInputLayout>(R.id.tilTelefono)
        val nombre = txt(R.id.etNombre); val paterno = txt(R.id.etPaterno); val tel = txt(R.id.etTelefono)
        tilNombre.error = if (nombre == null || nombre.length < 2) "Escribe tu nombre" else null
        tilPaterno.error = if (paterno == null) "Escribe tu apellido paterno" else null
        tilTel.error = if (tel != null && !Regex("^[0-9+\\s()-]{7,20}$").matches(tel)) "Teléfono no válido" else null
        if (tilNombre.error != null || tilPaterno.error != null || tilTel.error != null) {
            findViewById<androidx.core.widget.NestedScrollView>(R.id.scroll).smoothScrollTo(0, 0)
            return
        }
        val body = MeUpdate(
            usuario = mapOf("nombre" to nombre, "apellido_paterno" to paterno, "apellido_materno" to txt(R.id.etMaterno), "telefono" to tel),
            perfil = mapOf(
                "fecha_nacimiento" to fechaNac, "direccion" to txt(R.id.etDireccion), "ciudad" to txt(R.id.etCiudad),
                "codigo_postal" to txt(R.id.etCP),
                "tipo_vivienda" to findViewById<MaterialAutoCompleteTextView>(R.id.etTipoVivienda).text?.toString()?.ifBlank { null },
                "tiene_mascotas" to findViewById<MaterialSwitch>(R.id.swTieneMascotas).isChecked,
                "experiencia_mascotas" to txt(R.id.etExperiencia), "ocupacion" to txt(R.id.etOcupacion),
                "contacto_emergencia_nombre" to txt(R.id.etEmNombre), "contacto_emergencia_telefono" to txt(R.id.etEmTelefono),
                "contacto_emergencia_parentesco" to txt(R.id.etEmParentesco)
            )
        )
        val btn = findViewById<MaterialButton>(R.id.btnGuardar)
        btn.isEnabled = false
        findViewById<View>(R.id.progressTop).visibility = View.VISIBLE
        lifecycleScope.launch {
            when (val r = api { ApiClient.api.updateMe(body) }) {
                is Outcome.Ok -> {
                    Session.updateUser(r.value.usuario)
                    android.widget.Toast.makeText(this@EditProfileActivity, "Perfil actualizado", android.widget.Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }
                is Outcome.Fail -> {
                    r.applyTo(mapOf("nombre" to tilNombre, "apellido_paterno" to tilPaterno, "telefono" to tilTel))
                    findViewById<View>(android.R.id.content).showFail(r) { save() }
                }
            }
            btn.isEnabled = true
            findViewById<View>(R.id.progressTop).visibility = View.INVISIBLE
        }
    }
}
