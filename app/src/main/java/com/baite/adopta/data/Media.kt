
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class MediaException(message: String) : Exception(message)

object Media {
    private const val MAX_BYTES = 5 * 1024 * 1024
    private const val MAX_SIDE = 1600

    fun text(value: String?): RequestBody? =
        value?.trim()?.takeIf { it.isNotEmpty() }
            ?.toRequestBody("text/plain".toMediaType())

    fun mimeOf(context: Context, uri: Uri): String =
        context.contentResolver.getType(uri) ?: ""

    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    cursor.getString(index)?.let { return it }
                }
            }
        }
        return "archivo"
    }

    private fun readBytes(context: Context, uri: Uri): ByteArray {
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                it.readBytes()
            } ?: throw MediaException("Android no pudo abrir el archivo seleccionado")
        } catch (e: MediaException) {
            throw e
        } catch (e: Exception) {
            throw MediaException("No se pudo acceder al archivo: ${e.message ?: "error de lectura"}")
        }
    }

    suspend fun imagePart(
        context: Context,
        uri: Uri,
        field: String
    ): MultipartBody.Part = withContext(Dispatchers.IO) {

        val bytes = compress(context, uri)

        MultipartBody.Part.createFormData(
            field,
            "foto.jpg",
            bytes.toRequestBody("image/jpeg".toMediaType())
        )
    }

    suspend fun documentPart(
        context: Context,
        uri: Uri,
        field: String,
        allowPdf: Boolean
    ): MultipartBody.Part = withContext(Dispatchers.IO) {

        val mime = mimeOf(context, uri)
        val name = displayName(context, uri)

        when {
            mime.startsWith("image/") ||
                (mime.isEmpty() && name.lowercase().let {
                    it.endsWith(".jpg") ||
                    it.endsWith(".jpeg") ||
                    it.endsWith(".png")
                }) -> {

                val bytes = compress(context, uri)

                MultipartBody.Part.createFormData(
                    field,
                    name.substringBeforeLast('.') + ".jpg",
                    bytes.toRequestBody("image/jpeg".toMediaType())
                )
            }

            (mime == "application/pdf" ||
                name.lowercase().endsWith(".pdf")) && allowPdf -> {

                val bytes = readBytes(context, uri)

                if (bytes.size > MAX_BYTES) {
                    throw MediaException("El PDF supera los 5 MB")
                }

                MultipartBody.Part.createFormData(
                    field,
                    name,
                    bytes.toRequestBody("application/pdf".toMediaType())
                )
            }

            else -> {
                throw MediaException(
                    if (allowPdf) {
                        "Formato no permitido. Usa PDF, JPG o PNG."
                    } else {
                        "Solo se permiten fotos JPG o PNG."
                    }
                )
            }
        }
    }

    private fun compress(context: Context, uri: Uri): ByteArray {

        // Leer la URI una sola vez.
        val original = readBytes(context, uri)

        if (original.isEmpty()) {
            throw MediaException("La imagen está vacía")
        }

        // Obtener dimensiones sin decodificar toda la imagen.
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        BitmapFactory.decodeByteArray(
            original,
            0,
            original.size,
            bounds
        )

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw MediaException(
                "No se reconoce el formato de la imagen. Usa JPG o PNG."
            )
        }

        // Reducir la resolución antes de cargar la imagen completa.
        var sample = 1

        while (
            bounds.outWidth / sample > MAX_SIDE * 2 ||
            bounds.outHeight / sample > MAX_SIDE * 2
        ) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
        }

        var bitmap = BitmapFactory.decodeByteArray(
            original,
            0,
            original.size,
            options
        ) ?: throw MediaException("No se pudo decodificar la imagen")

        try {
            val scale = MAX_SIDE.toFloat() /
                maxOf(bitmap.width, bitmap.height)

            val matrix = Matrix()

            if (scale < 1f) {
                matrix.postScale(scale, scale)
            }

            // Corregir orientación EXIF.
            val orientation = try {
                ByteArrayInputStream(original).use { input ->
                    ExifInterface(input).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 ->
                    matrix.postRotate(90f)

                ExifInterface.ORIENTATION_ROTATE_180 ->
                    matrix.postRotate(180f)

                ExifInterface.ORIENTATION_ROTATE_270 ->
                    matrix.postRotate(270f)
            }

            if (!matrix.isIdentity) {
                val corrected = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                    matrix,
                    true
                )

                if (corrected !== bitmap) {
                    bitmap.recycle()
                    bitmap = corrected
                }
            }

            var quality = 85

            while (true) {
                val output = ByteArrayOutputStream()

                bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    quality,
                    output
                )

                val result = output.toByteArray()

                if (result.size <= MAX_BYTES) {
                    return result
                }

                if (quality <= 50) {
                    throw MediaException(
                        "La imagen es demasiado grande para subirla"
                    )
                }

                quality -= 10
            }
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }
}
