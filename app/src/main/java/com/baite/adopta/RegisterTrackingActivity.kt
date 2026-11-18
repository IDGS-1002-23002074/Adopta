package com.baite.adopta

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
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
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import java.io.File

/** RF028 — Registrar un reporte de adaptación (texto y hasta 5 fotos). */
class RegisterTrackingActivity : BaseActivity() {

    private val photos = mutableListOf<Uri>()
    private var cameraUri: Uri? = null
    private var sending = false

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { addPhoto(it) } }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let { addPhoto(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_tracking)
        val id = intent.getIntExtra(Extras.ADOPTION_ID, -1)
        if (id < 0) { finish(); return }
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.btnAddFoto).setOnClickListener { choosePhoto() }
        findViewById<MaterialButton>(R.id.btnEnviar).setOnClickListener { send(id) }
        renderPhotos()
    }

    private fun txt(id: Int) = findViewById<TextInputEditText>(id).text?.toString()

    private fun choosePhoto() {
        if (photos.size >= MAX_PHOTOS) {
            findViewById<View>(android.R.id.content)
                .snack("Máximo $MAX_PHOTOS fotos")
            return
        }

        MaterialAlertDialogBuilder(this)
            .setItems(arrayOf("Tomar una foto", "Elegir de la galería")) { _, which ->
                if (which == 0) {
                    try {
                        val dir = File(cacheDir, "camara").apply { mkdirs() }

                        val file = File(
                            dir,
                            "rep_${System.currentTimeMillis()}.jpg"
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

    private fun addPhoto(uri: Uri) {
        if (photos.size < MAX_PHOTOS) photos.add(uri)
        renderPhotos()
    }

    private fun renderPhotos() {
        val ll = findViewById<LinearLayout>(R.id.llFotos)
        ll.removeAllViews()
        photos.forEachIndexed { i, uri ->
            val v = LayoutInflater.from(this).inflate(R.layout.item_photo_thumb, ll, false)
            v.findViewById<ImageView>(R.id.ivFoto).setImageURI(uri)
            v.findViewById<View>(R.id.btnQuitar).setOnClickListener { photos.removeAt(i); renderPhotos() }
            ll.addView(v)
        }
        findViewById<View>(R.id.btnAddFoto).visible(photos.size < MAX_PHOTOS)
    }

    private fun send(id: Int) {
        if (sending) return
        val fields = listOf(R.id.etComportamiento, R.id.etSalud, R.id.etAlimentacion, R.id.etAdaptacion, R.id.etObservaciones).map { txt(it) }
        if (fields.all { it.isNullOrBlank() }) {
            findViewById<View>(android.R.id.content).snack("Llena al menos un campo para enviar el reporte")
            return
        }
        sending = true
        val btn = findViewById<MaterialButton>(R.id.btnEnviar)
        btn.isEnabled = false
        btn.text = "Enviando…"
        findViewById<View>(R.id.progressTop).visibility = View.VISIBLE
        lifecycleScope.launch {
            val parts = try {
                photos.map { Media.imagePart(this@RegisterTrackingActivity, it, "fotos") }
            } catch (e: MediaException) {
                findViewById<View>(android.R.id.content).snack(e.message ?: "Foto no válida")
                reset(btn); return@launch
            } catch (e: Exception) {
                findViewById<View>(android.R.id.content).snack("No se pudo leer una de las fotos")
                reset(btn); return@launch
            }
            val r = api {
                ApiClient.api.crearReporte(id, Media.text(fields[0]), Media.text(fields[1]), Media.text(fields[2]), Media.text(fields[3]), Media.text(fields[4]), parts as List<MultipartBody.Part>)
            }
            when (r) {
                is Outcome.Ok -> {
                    android.widget.Toast.makeText(this@RegisterTrackingActivity, "Reporte enviado. ¡Gracias!", android.widget.Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }
                is Outcome.Fail -> findViewById<View>(android.R.id.content).showFail(r) { send(id) }
            }
            reset(btn)
        }
    }

    private fun reset(btn: MaterialButton) {
        sending = false
        btn.isEnabled = true
        btn.text = "Enviar reporte"
        findViewById<View>(R.id.progressTop).visibility = View.INVISIBLE
    }

    private companion object { const val MAX_PHOTOS = 5 }
}
