package com.baite.adopta.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

/** Error con mensaje listo para mostrar al usuario (archivo no válido, muy grande...). */
class MediaException(message: String) : Exception(message)

/** Prepara fotos y documentos para subirlos (RNF017: formato y tamaño). */
object Media {
    private const val MAX_BYTES = 5 * 1024 * 1024
    private const val MAX_SIDE = 1600

    fun text(value: String?): RequestBody? =
        value?.trim()?.takeIf { it.isNotEmpty() }?.toRequestBody("text/plain".toMediaType())

    fun mimeOf(context: Context, uri: Uri): String = context.contentResolver.getType(uri) ?: ""

    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) c.getString(i)?.let { return it }
            }
        }
        return "archivo"
    }

    /** Foto: se reduce a 1600 px, se endereza según EXIF y se envía como JPEG (siempre < 5 MB). */
    suspend fun imagePart(context: Context, uri: Uri, field: String): MultipartBody.Part = withContext(Dispatchers.IO) {
        val bytes = compress(context, uri)
        MultipartBody.Part.createFormData(field, "foto.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
    }

    /**
     * Documento o foto. Acepta imágenes y PDF; los PDF se envían tal cual (máx. 5 MB).
     * [allowPdf] = false cuando el requisito solo admite imágenes.
     */
    suspend fun documentPart(context: Context, uri: Uri, field: String, allowPdf: Boolean): MultipartBody.Part =
        withContext(Dispatchers.IO) {
            val mime = mimeOf(context, uri)
            val name = displayName(context, uri)
            when {
                mime.startsWith("image/") || (mime.isEmpty() && name.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") }) -> {
                    val bytes = compress(context, uri)
                    MultipartBody.Part.createFormData(field, name.substringBeforeLast('.') + ".jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
                }
                (mime == "application/pdf" || name.lowercase().endsWith(".pdf")) && allowPdf -> {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw MediaException("No se pudo leer el archivo")
                    if (bytes.size > MAX_BYTES) throw MediaException("El PDF supera los 5 MB")
                    MultipartBody.Part.createFormData(field, name, bytes.toRequestBody("application/pdf".toMediaType()))
                }
                else -> throw MediaException(if (allowPdf) "Formato no permitido. Usa PDF, JPG o PNG." else "Solo se permiten fotos JPG o PNG.")
            }
        }

    private fun compress(context: Context, uri: Uri): ByteArray {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw MediaException("No se pudo leer la imagen")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw MediaException("El archivo no es una imagen válida")

        var sample = 1
        while (bounds.outWidth / sample > MAX_SIDE * 2 || bounds.outHeight / sample > MAX_SIDE * 2) sample *= 2
        var bmp = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw MediaException("No se pudo leer la imagen")

        val scale = MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
        val matrix = Matrix()
        if (scale < 1f) matrix.postScale(scale, scale)
        val orientation = try {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } catch (e: Exception) { null } ?: ExifInterface.ORIENTATION_NORMAL
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        if (!matrix.isIdentity) {
            val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            if (out !== bmp) bmp.recycle()
            bmp = out
        }
        var quality = 85
        while (true) {
            val stream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            val data = stream.toByteArray()
            if (data.size <= MAX_BYTES || quality <= 50) {
                bmp.recycle()
                if (data.size > MAX_BYTES) throw MediaException("La imagen es demasiado grande")
                return data
            }
            quality -= 10
        }
    }
}
