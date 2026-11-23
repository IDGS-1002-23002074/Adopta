package com.baite.adopta

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.Format
import com.baite.adopta.data.Media
import com.baite.adopta.data.MediaException
import com.baite.adopta.data.NuevaReferencia
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.Requisito
import com.baite.adopta.data.SolicitudDetalle
import com.baite.adopta.data.api
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

/**
 * Módulo 5 — Documentación del adoptante: checklist de documentos (con cámara, galería o PDF),
 * información del hogar, referencias personales y envío a revisión.
 */
class DocumentationActivity : BaseActivity() {

    private var id = -1
    private var detalle: SolicitudDetalle? = null
    private var pendingReq: Requisito? = null
    private var pendingFecha: String? = null
    private var cameraUri: Uri? = null
    private var busy = false

    private val pickDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { upload(it) } }
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { upload(it) } }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let { upload(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_documentation)
        id = intent.getIntExtra(Extras.REQUEST_ID, -1)
        if (id < 0) { finish(); return }
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.btnInfo).setOnClickListener {
            startActivity(Intent(this, HouseholdFormActivity::class.java).putExtra(Extras.REQUEST_ID, id))
        }
        findViewById<View>(R.id.btnAddRef).setOnClickListener { addReferenceDialog() }
        findViewById<View>(R.id.btnEnviar).setOnClickListener { confirmSend() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun setBusy(value: Boolean) {
        busy = value
        findViewById<LinearProgressIndicator>(R.id.progressTop).visibility = if (value) View.VISIBLE else View.INVISIBLE
    }

    private fun load() {
        setBusy(true)
        lifecycleScope.launch {
            when (val r = api { ApiClient.api.solicitud(id) }) {
                is Outcome.Ok -> render(r.value)
                is Outcome.Fail -> findViewById<View>(android.R.id.content).showFail(r) { load() }
            }
            setBusy(false)
        }
    }

    /** Aplica el resultado de cualquier operación (el servidor siempre devuelve la solicitud completa). */
    private fun handle(r: Outcome<SolicitudDetalle>, okMessage: String? = null) {
        when (r) {
            is Outcome.Ok -> { render(r.value); okMessage?.let { findViewById<View>(android.R.id.content).snack(it) } }
            is Outcome.Fail -> findViewById<View>(android.R.id.content).showFail(r)
        }
    }

    private fun render(d: SolicitudDetalle) {
        detalle = d
        val editable = d.puedeEditarDocumentos
        val obligatorios = d.requisitos.filter { it.obligatorio }
        val infoOk = d.informacion?.estadoFormulario == "Enviado"
        val total = obligatorios.size + 1 + 2
        val done = obligatorios.count { it.cumple } + (if (infoOk) 1 else 0) + minOf(d.referencias.size, 2)
        findViewById<LinearProgressIndicator>(R.id.progChecklist).setProgressCompat(done * 100 / total, true)
        findViewById<TextView>(R.id.tvResumenDocs).text = when {
            !editable && d.estado == "En revision" -> "Documentación enviada. ${d.organizacion} la está revisando."
            !editable -> "Esta solicitud ya no admite cambios (${Format.estado(d.estado)})."
            else -> "Llevas $done de $total pasos"
        }
        findViewById<TextView>(R.id.tvPlazo).apply {
            val limite = d.fechaLimiteDocumentos
            visible(editable && !limite.isNullOrBlank())
            text = "Solicitud de ${d.mascota} · Fecha límite: ${Format.date(limite)}"
        }

        // Documentos
        val ll = findViewById<LinearLayout>(R.id.llRequisitos)
        ll.removeAllViews()
        d.requisitos.forEach { r -> ll.addView(requisitoView(ll, r, editable)) }

        // Información del hogar
        val info = d.informacion
        findViewById<TextView>(R.id.tvInfoEstado).apply {
            when {
                infoOk -> applyTone("Completa", Format.Tone.GOOD)
                info != null -> applyTone("Borrador", Format.Tone.NEUTRAL)
                else -> applyTone("Pendiente", Format.Tone.WARN)
            }
        }
        findViewById<MaterialButton>(R.id.btnInfo).apply {
            text = if (infoOk) "Revisar información" else if (info != null) "Continuar información" else "Completar información"
            isEnabled = editable
        }

        // Referencias
        val lr = findViewById<LinearLayout>(R.id.llReferencias)
        lr.removeAllViews()
        d.referencias.forEach { ref ->
            val v = LayoutInflater.from(this).inflate(R.layout.item_referencia, lr, false)
            v.findViewById<TextView>(R.id.tvNombre).text = ref.nombreCompleto
            v.findViewById<TextView>(R.id.tvDetalle).text =
                listOfNotNull(ref.relacion, ref.telefono, ref.aniosConocidos?.let { "$it años de conocerse" }).joinToString(" · ")
            v.findViewById<View>(R.id.btnEliminar).apply {
                visible(editable)
                setOnClickListener {
                    MaterialAlertDialogBuilder(this@DocumentationActivity)
                       .setTitle("¿Quitar esta referencia?")
                       .setPositiveButton("Quitar") { _, _ ->
                          mutate { ApiClient.api.quitarReferencia(this@DocumentationActivity.id, ref.idReferencia) }
                        }
                       .setNegativeButton("Cancelar", null).show()
                }
            }
            lr.addView(v)
        }
        findViewById<View>(R.id.btnAddRef).visible(editable && d.referencias.size < 5)

        // Envío
        val btn = findViewById<MaterialButton>(R.id.btnEnviar)
        btn.isEnabled = d.puedeEnviar
        btn.text = if (editable) "Enviar para revisión" else "Documentación enviada"
        findViewById<TextView>(R.id.tvFaltan).apply {
            visible(editable && !d.puedeEnviar)
            val faltan = mutableListOf<String>()
            if (d.pendientes.documentos > 0) faltan += "${d.pendientes.documentos} documento(s)"
            if (d.pendientes.informacion) faltan += "información del hogar"
            if (d.pendientes.referencias > 0) faltan += "${d.pendientes.referencias} referencia(s)"
            text = "Para enviar te falta: ${faltan.joinToString(", ")}"
        }
    }

    private fun requisitoView(parent: LinearLayout, r: Requisito, editable: Boolean): View {
        val v = LayoutInflater.from(this).inflate(R.layout.item_requisito, parent, false)
        v.findViewById<TextView>(R.id.tvNombre).text = r.nombre + if (r.obligatorio) "" else " (opcional)"
        v.findViewById<TextView>(R.id.tvEstado).applyStatus(if (r.cumple && r.estado == "Pendiente") "Cargado" else r.estado)
        v.findViewById<TextView>(R.id.tvDescripcion).apply { visible(!r.descripcion.isNullOrBlank()); text = r.descripcion }
        val formatos = r.formatosPermitidos.split(",").joinToString(", ") { it.trim().uppercase() }
        v.findViewById<TextView>(R.id.tvReglas).text = buildString {
            append("$formatos · máx. ${r.tamanoMaxMb} MB")
            if (r.maxArchivos > 1) append(" · de ${r.minArchivos} a ${r.maxArchivos} archivos")
            if (r.vigenciaMeses != null) append(" · antigüedad máx. ${r.vigenciaMeses} meses")
        }
        v.findViewById<TextView>(R.id.tvMotivo).apply {
            visible(r.estado == "Rechazado")
            text = "Rechazado: ${r.motivoRechazo ?: "súbelo de nuevo"}"
        }
        val lf = v.findViewById<LinearLayout>(R.id.llArchivos)
        val locked = !editable || r.estado == "Aprobado"
        r.archivos.forEach { a ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_archivo, lf, false)
            row.findViewById<TextView>(R.id.tvNombre).text = a.nombreOriginal
            row.findViewById<TextView>(R.id.tvInfo).text = "${a.extension.uppercase()} · ${a.tamanoBytes / 1024} KB · ${Format.date(a.fechaCarga)}"
            row.findViewById<View>(R.id.btnEliminar).apply {
                visible(!locked)
                setOnClickListener { mutate { ApiClient.api.quitarArchivo(this@DocumentationActivity.id, a.idArchivo) } }
            }
            lf.addView(row)
        }
        val btn = v.findViewById<MaterialButton>(R.id.btnAdjuntar)
        val lleno = r.archivos.size >= r.maxArchivos && r.maxArchivos > 1
        btn.visible(!locked && !lleno)
        btn.text = if (r.archivos.isNotEmpty() && r.maxArchivos == 1) "Reemplazar archivo" else "Adjuntar archivo"
        btn.setOnClickListener { chooseSource(r) }
        return v
    }

    private fun mutate(call: suspend () -> SolicitudDetalle) {
        if (busy) return
        setBusy(true)
        lifecycleScope.launch {
            handle(api { call() })
            setBusy(false)
        }
    }

    // ---------------------------------------------------------------- adjuntar archivos

    private fun chooseSource(r: Requisito) {
        if (busy) return
        pendingReq = r
        val allowPdf = "pdf" in r.formatosPermitidos.lowercase()
        val options = mutableListOf("Tomar una foto", "Elegir de la galería")
        if (allowPdf) options += "Elegir un PDF o archivo"
        MaterialAlertDialogBuilder(this)
            .setTitle("Adjuntar a: ${r.nombre}")
            .setItems(options.toTypedArray()) { _, which ->
                val launch = {
                    when (which) {
                        0 -> openCamera()
                        1 -> pickImage.launch("image/*")
                        else -> pickDoc.launch(arrayOf("application/pdf", "image/*"))
                    }
                }
                if (r.requiereFechaEmision) askIssueDate(launch) else { pendingFecha = null; launch() }
            }
            .show()
    }

    private fun askIssueDate(then: () -> Unit) {
        val c = Calendar.getInstance()
        val dlg = DatePickerDialog(this, { _, y, m, d ->
            pendingFecha = Format.iso(y, m, d)
            then()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
        dlg.setTitle("¿Qué fecha de emisión tiene el documento?")
        dlg.datePicker.maxDate = System.currentTimeMillis()
        dlg.show()
    }

    private fun openCamera() {
        try {
            val dir = File(cacheDir, "camara").apply { mkdirs() }
            val file = File(dir, "doc_${System.currentTimeMillis()}.jpg")

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
    }

    private fun upload(uri: Uri) {
        val req = pendingReq ?: return
        if (busy) return
        setBusy(true)
        lifecycleScope.launch {
            val part = try {
                Media.documentPart(this@DocumentationActivity, uri, "archivo", allowPdf = "pdf" in req.formatosPermitidos.lowercase())
            } catch (e: MediaException) {
                findViewById<View>(android.R.id.content).snack(e.message ?: "Archivo no válido")
                setBusy(false)
                return@launch
            } catch (e: Exception) {
                findViewById<View>(android.R.id.content).snack("No se pudo leer el archivo")
                setBusy(false)
                return@launch
            }
            handle(api { ApiClient.api.subirArchivo(id, req.idRequisito, part, Media.text(pendingFecha)) }, "Archivo cargado")
            setBusy(false)
        }
    }

    // ---------------------------------------------------------------- referencias

    private fun addReferenceDialog() {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_referencia, null)
        val dlg = MaterialAlertDialogBuilder(this)
            .setTitle("Nueva referencia")
            .setView(v)
            .setPositiveButton("Agregar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.show()
        dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val nombre = v.findViewById<TextInputEditText>(R.id.etRefNombre).text?.toString().orEmpty().trim()
            val tel = v.findViewById<TextInputEditText>(R.id.etRefTelefono).text?.toString().orEmpty().trim()
            val rel = v.findViewById<TextInputEditText>(R.id.etRefRelacion).text?.toString().orEmpty().trim()
            val anios = v.findViewById<TextInputEditText>(R.id.etRefAnios).text?.toString().orEmpty().trim().toIntOrNull()
            val tilN = v.findViewById<TextInputLayout>(R.id.tilRefNombre)
            val tilT = v.findViewById<TextInputLayout>(R.id.tilRefTelefono)
            val tilR = v.findViewById<TextInputLayout>(R.id.tilRefRelacion)
            tilN.error = if (nombre.length < 5) "Escribe el nombre completo" else null
            tilT.error = if (!Regex("^\\d{10}$").matches(tel)) {"El teléfono debe tener exactamente 10 dígitos"} else {null}
            tilR.error = if (rel.isEmpty()) "Indica la relación" else null
            if (tilN.error != null || tilT.error != null || tilR.error != null) return@setOnClickListener
            dlg.dismiss()
            mutate { ApiClient.api.agregarReferencia(id, NuevaReferencia(nombre, tel, rel, anios)) }
        }
    }

    // ---------------------------------------------------------------- enviar

    private fun confirmSend() {
        val d = detalle ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Enviar a revisión?")
            .setMessage("${d.organizacion} revisará tus documentos. Mientras lo hace no podrás modificarlos.")
            .setPositiveButton("Enviar") { _, _ ->
                setBusy(true)
                lifecycleScope.launch {
                    when (val r = api { ApiClient.api.enviarDocumentacion(id) }) {
                        is Outcome.Ok -> {
                            render(r.value)
                            MaterialAlertDialogBuilder(this@DocumentationActivity)
                                .setTitle("¡Documentación enviada!")
                                .setMessage("Te avisaremos cuando ${r.value.organizacion} termine la revisión.")
                                .setPositiveButton("Entendido") { _, _ -> finish() }
                                .show()
                        }
                        is Outcome.Fail -> {
                            if (r.fields.isNotEmpty()) {
                                MaterialAlertDialogBuilder(this@DocumentationActivity)
                                    .setTitle("Aún no puedes enviar")
                                    .setMessage(r.fields.values.joinToString("\n• ", prefix = "• "))
                                    .setPositiveButton("Entendido", null).show()
                            } else findViewById<View>(android.R.id.content).showFail(r)
                            load()
                        }
                    }
                    setBusy(false)
                }
            }
            .setNegativeButton("Volver", null)
            .show()
    }
}
