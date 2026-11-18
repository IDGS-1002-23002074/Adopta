package com.baite.adopta

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.Media
import com.baite.adopta.data.MediaException
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.api
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.io.File

/** RF039/RF040 — Crear o editar una publicación (texto y foto opcional). */
class PostEditorActivity : BaseActivity() {

    private var postId = -1
    private var newImage: Uri? = null
    private var currentImageUrl: String? = null
    private var removeImage = false
    private var cameraUri: Uri? = null
    private var sending = false

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { setImage(it) } }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let { setImage(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post_editor)
        postId = intent.getIntExtra(Extras.POST_ID, -1)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        if (postId >= 0) {
            toolbar.title = "Editar publicación"
            findViewById<MaterialButton>(R.id.btnPublicar).text = "Guardar cambios"
            loadPost()
        }
        findViewById<TextInputEditText>(R.id.etContenido).doAfterTextChanged { findViewById<TextInputLayout>(R.id.tilContenido).error = null }
        findViewById<View>(R.id.btnImagen).setOnClickListener { chooseImage() }
        findViewById<View>(R.id.btnQuitarImagen).setOnClickListener {
            newImage = null
            removeImage = true
            renderImage()
        }
        findViewById<View>(R.id.btnPublicar).setOnClickListener { publish() }
    }

    private fun loadPost() {
        lifecycleScope.launch {
            when (val r = api { ApiClient.api.publicacion(postId) }) {
                is Outcome.Ok -> {
                    findViewById<TextInputEditText>(R.id.etContenido).setText(r.value.contenido)
                    currentImageUrl = r.value.imagen
                    renderImage()
                }
                is Outcome.Fail -> findViewById<View>(android.R.id.content).showFail(r) { loadPost() }
            }
        }
    }

    private fun chooseImage() {
        MaterialAlertDialogBuilder(this)
            .setItems(arrayOf("Tomar una foto", "Elegir de la galería")) { _, which ->
                if (which == 0) {
                    try {
                        val dir = File(cacheDir, "camara").apply { mkdirs() }

                        val file = File(
                            dir,
                            "post_${System.currentTimeMillis()}.jpg"
                        )

                        val uri = FileProvider.getUriForFile(
                            this,
                            "$packageName.fileprovider",
                            file
                        )

                        cameraUri = uri
                        takePhoto.launch(uri)

                    } catch (e: Exception) {
                        findViewById<View>(android.R.id.content)
                            .snack("No se pudo abrir la cámara")
                    }
                } else {
                    pickImage.launch("image/*")
                }
            }
            .show()
    }

    private fun setImage(uri: Uri) {
        newImage = uri
        removeImage = false
        renderImage()
    }

    private fun renderImage() {
        val frame = findViewById<View>(R.id.frameImagen)
        val iv = findViewById<ImageView>(R.id.ivPreview)
        when {
            newImage != null -> { frame.visible(true); iv.setImageURI(newImage) }
            currentImageUrl != null && !removeImage -> { frame.visible(true); iv.loadPhoto(currentImageUrl) }
            else -> frame.visible(false)
        }
        findViewById<MaterialButton>(R.id.btnImagen).text = if (frame.visibility == View.VISIBLE) "Cambiar foto" else "Agregar foto"
    }

    private fun publish() {
        if (sending) return
        val texto = findViewById<TextInputEditText>(R.id.etContenido).text?.toString().orEmpty().trim()
        if (texto.isEmpty()) {
            findViewById<TextInputLayout>(R.id.tilContenido).error = "Escribe algo para publicar"
            return
        }
        sending = true
        val btn = findViewById<MaterialButton>(R.id.btnPublicar)
        btn.isEnabled = false
        findViewById<View>(R.id.progressTop).visibility = View.VISIBLE
        lifecycleScope.launch {
            val part = try {
                newImage?.let { Media.imagePart(this@PostEditorActivity, it, "imagen") }
            } catch (e: MediaException) {
                findViewById<View>(android.R.id.content).snack(e.message ?: "Foto no válida"); done(btn); return@launch
            } catch (e: Exception) {
                findViewById<View>(android.R.id.content).snack("No se pudo leer la foto"); done(btn); return@launch
            }
            val body = Media.text(texto)!!
            val r = api {
                if (postId >= 0) ApiClient.api.editarPublicacion(postId, body, if (removeImage) Media.text("true") else null, part)
                else ApiClient.api.crearPublicacion(body, part)
            }
            when (r) {
                is Outcome.Ok -> { setResult(Activity.RESULT_OK); finish(); return@launch }
                is Outcome.Fail -> {
                    findViewById<TextInputLayout>(R.id.tilContenido).error = r.fields["contenido"]
                    findViewById<View>(android.R.id.content).showFail(r) { publish() }
                }
            }
            done(btn)
        }
    }

    private fun done(btn: MaterialButton) {
        sending = false
        btn.isEnabled = true
        findViewById<View>(R.id.progressTop).visibility = View.INVISIBLE
    }
}
