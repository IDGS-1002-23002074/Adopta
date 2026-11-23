package com.baite.adopta

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/** Editar perfil: GET /api/me, PUT /api/me (solo campos cambiados) y POST /api/me/foto. */
class EditProfileActivity : AppCompatActivity() {

    /** Valores tal como llegaron del servidor, para enviar solo lo que cambió. */
    private data class Snapshot(
        val nombre: String = "",
        val apellidos: String = "",
        val telefono: String = "",
        val ciudad: String = ""
    )

    private lateinit var tilNombre: TextInputLayout
    private lateinit var tilApellidos: TextInputLayout
    private lateinit var tilTelefono: TextInputLayout
    private lateinit var tilCiudad: TextInputLayout
    private lateinit var etNombre: EditText
    private lateinit var etApellidos: EditText
    private lateinit var etEmail: EditText
    private lateinit var etTelefono: EditText
    private lateinit var etCiudad: EditText
    private lateinit var ivAvatar: ImageView
    private lateinit var tvCambiarFoto: TextView
    private lateinit var btnGuardar: MaterialButton

    private var original = Snapshot()
    private var loaded = false
    private var saving = false
    private var uploading = false
    private val worker = Executors.newSingleThreadExecutor()

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) uploadPhoto(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        tilNombre = findViewById(R.id.tilNombre)
        tilApellidos = findViewById(R.id.tilApellidos)
        tilTelefono = findViewById(R.id.tilTelefono)
        tilCiudad = findViewById(R.id.tilCiudad)
        etNombre = findViewById(R.id.etNombre)
        etApellidos = findViewById(R.id.etApellidos)
        etEmail = findViewById(R.id.etEmail)
        etTelefono = findViewById(R.id.etTelefono)
        etCiudad = findViewById(R.id.etCiudad)
        ivAvatar = findViewById(R.id.ivAvatar)
        tvCambiarFoto = findViewById(R.id.tvCambiarFoto)
        btnGuardar = findViewById(R.id.btnGuardar)

        // Mientras llega el perfil completo se muestra lo que ya se conoce de la sesión.
        etEmail.setText(Session.correo)
        showAvatar(Session.foto)
        btnGuardar.isEnabled = false

        tvCambiarFoto.setOnClickListener { pickPhoto() }
        ivAvatar.setOnClickListener { pickPhoto() }
        btnGuardar.setOnClickListener { save() }

        loadProfile()
    }

    override fun onDestroy() {
        super.onDestroy()
        worker.shutdown()
    }

    // ------------------------------------------------------------------ cargar

    private fun loadProfile() {
        Api.call("GET", "/api/me",
            onError = { e ->
                if (!isDestroyed) Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            }
        ) { r ->
            if (isDestroyed) return@call
            val u = r.optJSONObject("usuario") ?: JSONObject()
            val p = r.optJSONObject("perfil") ?: JSONObject()
            Session.updateProfile(u)

            original = Snapshot(
                nombre = u.str("nombre") ?: "",
                apellidos = listOfNotNull(u.str("apellido_paterno"), u.str("apellido_materno")).joinToString(" "),
                telefono = u.str("telefono") ?: "",
                ciudad = p.str("ciudad") ?: ""
            )
            etNombre.setText(original.nombre)
            etApellidos.setText(original.apellidos)
            etEmail.setText(u.str("correo") ?: Session.correo)
            etTelefono.setText(original.telefono)
            etCiudad.setText(original.ciudad)
            showAvatar(u.str("foto_perfil"))

            loaded = true
            btnGuardar.isEnabled = true
        }
    }

    // ------------------------------------------------------------------ guardar

    private fun save() {
        if (!loaded || saving) return
        listOf(tilNombre, tilApellidos, tilTelefono, tilCiudad).forEach { it.error = null }

        val nombre = etNombre.text?.toString()?.trim().orEmpty()
        val apellidos = etApellidos.text?.toString()?.trim().orEmpty().replace(Regex("\\s+"), " ")
        val telefono = etTelefono.text?.toString()?.trim().orEmpty()
        val ciudad = etCiudad.text?.toString()?.trim().orEmpty()

        var valid = true
        if (nombre.length < 2) {
            tilNombre.error = "Escribe tu nombre"
            valid = false
        }
        if (apellidos.isEmpty()) {
            tilApellidos.error = "Escribe tus apellidos"
            valid = false
        }
        if (telefono.isNotEmpty() && !PHONE.matches(telefono)) {
            tilTelefono.error = "Teléfono no válido"
            valid = false
        }
        if (ciudad.length > 80) {
            tilCiudad.error = "Máximo 80 caracteres"
            valid = false
        }
        if (!valid) return

        // Solo se envía lo que cambió: así un apellido compuesto ("De la Cruz") no se parte
        // por error cuando el usuario solo editó el teléfono.
        val usuario = JSONObject()
        val perfil = JSONObject()
        if (nombre != original.nombre) usuario.put("nombre", nombre)
        if (apellidos != original.apellidos) {
            val partes = apellidos.split(" ", limit = 2)
            usuario.put("apellido_paterno", partes[0])
            usuario.put("apellido_materno", partes.getOrElse(1) { "" })
        }
        if (telefono != original.telefono) usuario.put("telefono", telefono)
        if (ciudad != original.ciudad) perfil.put("ciudad", ciudad)

        if (usuario.length() == 0 && perfil.length() == 0) {
            Toast.makeText(this, "No hay cambios por guardar", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val body = JSONObject()
        if (usuario.length() > 0) body.put("usuario", usuario)
        if (perfil.length() > 0) body.put("perfil", perfil)

        setSaving(true)
        Api.call("PUT", "/api/me", body,
            onError = { e ->
                if (isDestroyed) return@call
                setSaving(false)
                showServerErrors(e)
            },
            onOk = { r ->
                if (isDestroyed) return@call
                r.optJSONObject("usuario")?.let { Session.updateProfile(it) }
                Toast.makeText(this, "Cambios guardados", Toast.LENGTH_SHORT).show()
                finish()
            })
    }

    /** Errores por campo del backend (422 { detalles: { nombre: "..." } }). */
    private fun showServerErrors(e: ApiException) {
        e.details["nombre"]?.let { tilNombre.error = it }
        e.details["apellido_paterno"]?.let { tilApellidos.error = it }
        e.details["telefono"]?.let { tilTelefono.error = it }
        if (e.details.isEmpty()) Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
    }

    private fun setSaving(value: Boolean) {
        saving = value
        btnGuardar.isEnabled = !value
        btnGuardar.text = if (value) "Guardando…" else "Guardar cambios"
    }

    // ------------------------------------------------------------------ foto

    private fun pickPhoto() {
        if (!uploading) pickImage.launch("image/*")
    }

    /**
     * La API solo acepta JPG/PNG de hasta 5 MB. Las fotos del celular suelen pesar más (o ser
     * HEIC/WEBP), así que se reduce a 1080 px y se envía como JPEG. Glide también corrige la rotación.
     */
    private fun uploadPhoto(uri: Uri) {
        setUploading(true)
        worker.execute {
            val bytes: ByteArray? = try {
                val bmp = Glide.with(applicationContext).asBitmap().load(uri)
                    .override(1080, 1080).fitCenter().submit().get()
                ByteArrayOutputStream().use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    out.toByteArray()
                }
            } catch (e: Exception) {
                null
            }
            if (bytes == null) {
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    setUploading(false)
                    Toast.makeText(this, "No se pudo leer la imagen", Toast.LENGTH_LONG).show()
                }
                return@execute
            }
            Api.upload("/api/me/foto", "foto", "foto.jpg", "image/jpeg", bytes,
                onError = { e ->
                    if (isDestroyed) return@upload
                    setUploading(false)
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                },
                onOk = { r ->
                    if (isDestroyed) return@upload
                    val url = r.str("foto_perfil")
                    Session.setFoto(url)
                    showAvatar(url)
                    setUploading(false)
                    Toast.makeText(this, "Foto actualizada", Toast.LENGTH_SHORT).show()
                })
        }
    }

    private fun setUploading(value: Boolean) {
        uploading = value
        tvCambiarFoto.text = if (value) "Subiendo foto…" else "Cambiar foto"
    }

    private fun showAvatar(url: String?) {
        if (url.isNullOrBlank()) {
            Glide.with(this).clear(ivAvatar)
            ivAvatar.setImageDrawable(null)
        } else {
            Glide.with(this).load(url).centerCrop().into(ivAvatar)
        }
    }

    private companion object {
        val PHONE = Regex("^[0-9+\\s()-]{7,20}$")
    }
}
