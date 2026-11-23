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
import androidx.core.widget.NestedScrollView
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
import com.google.android.material.checkbox.MaterialCheckBox
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
    private fun til(i: Int) = findViewById<TextInputLayout>(i)
    private fun txt(i: Int) = et(i).text?.toString()?.trim()?.ifEmpty { null }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

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
        fechaNac = p.fechaNacimiento?.take(10)
        et(R.id.etFechaNac).setText(fechaNac?.let { Format.date(it) }.orEmpty())
        et(R.id.etOcupacion).setText(p.ocupacion.orEmpty())
        et(R.id.etTelefono).setText(u.telefono.orEmpty())

        et(R.id.etDireccion).setText(p.direccion.orEmpty())
        et(R.id.etCiudad).setText(p.ciudad.orEmpty())
        et(R.id.etCP).setText(p.codigoPostal.orEmpty())
        findViewById<MaterialAutoCompleteTextView>(R.id.etTipoVivienda).setText(p.tipoVivienda.orEmpty(), false)

        findViewById<MaterialCheckBox>(R.id.cbTieneMascotas).isChecked = p.tieneMascotas
        et(R.id.etExperiencia).setText(p.experienciaMascotas.orEmpty())

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
            ?: c.add(Calendar.YEAR, -18)
        val dlg = DatePickerDialog(this, { _, y, m, d ->
            fechaNac = Format.iso(y, m, d)
            et(R.id.etFechaNac).setText(Format.date(fechaNac))
            til(R.id.tilFechaNac).error = null
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

    private fun esMayorDeEdad(isoDate: String): Boolean {
        val parts = isoDate.split("-")
        if (parts.size != 3) return false
        val nac = Calendar.getInstance().apply {
            set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        }
        val limite = Calendar.getInstance().apply {
            add(Calendar.YEAR, -18)
        }
        return !nac.after(limite)
    }

    private fun telefonoValido(tel: String): Boolean {
        if (!Regex("^[0-9+\\s()-]+$").matches(tel)) return false
        val digitos = tel.count { it.isDigit() }
        return digitos in 10..15
    }

    private fun save() {
        val soloLetras = Regex("^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s'-]+$")

        val tilNombre = til(R.id.tilNombre)
        val tilPaterno = til(R.id.tilPaterno)
        val tilMaterno = til(R.id.tilMaterno)
        val tilFechaNac = til(R.id.tilFechaNac)
        val tilTel = til(R.id.tilTelefono)
        val tilCiudad = til(R.id.tilCiudad)
        val tilCP = til(R.id.tilCP)
        val tilEmNombre = til(R.id.tilEmNombre)
        val tilEmTel = til(R.id.tilEmTelefono)
        val tilEmParentesco = til(R.id.tilEmParentesco)

        val nombre = txt(R.id.etNombre)
        val paterno = txt(R.id.etPaterno)
        val materno = txt(R.id.etMaterno)
        val tel = txt(R.id.etTelefono)
        val ciudad = txt(R.id.etCiudad)
        val cp = txt(R.id.etCP)
        val emNombre = txt(R.id.etEmNombre)
        val emTel = txt(R.id.etEmTelefono)
        val emParentesco = txt(R.id.etEmParentesco)

        tilNombre.error = when {
            nombre == null || nombre.length < 2 -> "Escribe tu nombre (mínimo 2 caracteres)"
            !soloLetras.matches(nombre) -> "El nombre solo debe contener letras"
            else -> null
        }
        tilPaterno.error = when {
            paterno == null || paterno.length < 2 -> "Escribe tu apellido paterno"
            !soloLetras.matches(paterno) -> "El apellido solo debe contener letras"
            else -> null
        }
        tilMaterno.error = when {
            materno != null && materno.length < 2 -> "Mínimo 2 caracteres"
            materno != null && !soloLetras.matches(materno) -> "El apellido solo debe contener letras"
            else -> null
        }
        tilFechaNac.error = when {
            fechaNac != null && !esMayorDeEdad(fechaNac!!) -> "Debes ser mayor de 18 años"
            else -> null
        }
        tilTel.error = when {
            tel != null && !telefonoValido(tel) -> "Ingresa un teléfono válido de 10 dígitos"
            else -> null
        }
        tilCiudad.error = when {
            ciudad != null && ciudad.length < 2 -> "Escribe una ciudad válida"
            else -> null
        }
        tilCP.error = when {
            cp != null && !Regex("^\\d{5}$").matches(cp) -> "El código postal debe tener 5 dígitos"
            else -> null
        }
        tilEmNombre.error = when {
            emNombre != null && emNombre.length < 2 -> "Escribe el nombre del contacto"
            emNombre != null && !soloLetras.matches(emNombre) -> "El nombre solo debe contener letras"
            emNombre == null && (emTel != null || emParentesco != null) -> "Escribe el nombre de tu contacto de emergencia"
            else -> null
        }
        tilEmTel.error = when {
            emTel != null && !telefonoValido(emTel) -> "Ingresa un teléfono válido de 10 dígitos"
            emTel == null && emNombre != null -> "Ingresa el teléfono de tu contacto de emergencia"
            else -> null
        }
        tilEmParentesco.error = when {
            emParentesco != null && !soloLetras.matches(emParentesco) -> "Solo se permiten letras"
            else -> null
        }

        val campos = listOf(
            tilNombre, tilPaterno, tilMaterno, tilFechaNac, tilTel,
            tilCiudad, tilCP, tilEmNombre, tilEmTel, tilEmParentesco
        )
        val primerError = campos.firstOrNull { it.error != null }
        if (primerError != null) {
            findViewById<NestedScrollView>(R.id.scroll).smoothScrollTo(0, primerError.top)
            primerError.requestFocus()
            return
        }

        val body = MeUpdate(
            usuario = mapOf(
                "nombre" to nombre,
                "apellido_paterno" to paterno,
                "apellido_materno" to materno,
                "telefono" to tel
            ),
            perfil = mapOf(
                "fecha_nacimiento" to fechaNac,
                "direccion" to txt(R.id.etDireccion),
                "ciudad" to ciudad,
                "codigo_postal" to cp,
                "tipo_vivienda" to findViewById<MaterialAutoCompleteTextView>(R.id.etTipoVivienda).text?.toString()?.ifBlank { null },
                "tiene_mascotas" to findViewById<MaterialCheckBox>(R.id.cbTieneMascotas).isChecked,
                "experiencia_mascotas" to txt(R.id.etExperiencia),
                "ocupacion" to txt(R.id.etOcupacion),
                "contacto_emergencia_nombre" to emNombre,
                "contacto_emergencia_telefono" to emTel,
                "contacto_emergencia_parentesco" to emParentesco
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
                    r.applyTo(
                        mapOf(
                            "nombre" to tilNombre,
                            "apellido_paterno" to tilPaterno,
                            "apellido_materno" to tilMaterno,
                            "telefono" to tilTel,
                            "fecha_nacimiento" to tilFechaNac,
                            "codigo_postal" to tilCP,
                            "contacto_emergencia_telefono" to tilEmTel
                        )
                    )
                    findViewById<View>(android.R.id.content).showFail(r) { save() }
                }
            }
            btn.isEnabled = true
            findViewById<View>(R.id.progressTop).visibility = View.INVISIBLE
        }
    }
}