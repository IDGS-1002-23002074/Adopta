package com.baite.adopta.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formatos de fecha y textos de presentación para los datos del backend. */
object Format {
    private val es = Locale("es", "MX")
    private val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val dateOnly = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val shortFmt = SimpleDateFormat("d MMM yyyy", es)

    fun parse(s: String?): Date? {
        if (s.isNullOrBlank()) return null
        return try {
            synchronized(dateTime) { if (s.length > 10) dateTime.parse(s.take(19).replace('T', ' ')) else dateOnly.parse(s) }
        } catch (e: Exception) { null }
    }

    /** "12 oct 2026" */
    fun date(s: String?): String = parse(s)?.let { synchronized(shortFmt) { shortFmt.format(it) } } ?: ""

    /** "Hace 5 min", "Hace 2 h", "Ayer" o la fecha. */
    fun relative(s: String?): String {
        val d = parse(s) ?: return ""
        val mins = (System.currentTimeMillis() - d.time) / 60000
        return when {
            mins < 1 -> "Ahora"
            mins < 60 -> "Hace $mins min"
            mins < 60 * 24 -> "Hace ${mins / 60} h"
            mins < 60 * 48 -> "Ayer"
            else -> date(s)
        }
    }

    /** Fecha para enviar al servidor (AAAA-MM-DD). */
    fun iso(year: Int, month0: Int, day: Int): String = String.format(Locale.US, "%04d-%02d-%02d", year, month0 + 1, day)

    fun tamano(db: String?): String = when (db) { "Pequeno" -> "Pequeño"; null -> ""; else -> db }
    fun tamanoDb(label: String?): String? = when (label) { "Pequeño" -> "Pequeno"; "" -> null; else -> label }

    /** Datos de la mascota en una línea: "Perro · Mediano · 2 años". */
    fun resumenMascota(especie: String, tamano: String?, edad: String?): String =
        listOf(especie, tamano(tamano), edad ?: "").filter { it.isNotBlank() }.joinToString(" · ")

    /** Los estados del catálogo van sin acentos en la BD; aquí se muestran bien escritos. */
    fun estado(db: String?): String = when (db) {
        "En revision" -> "En revisión"
        "Documentacion pendiente" -> "Documentación pendiente"
        "En proceso de adopcion" -> "En proceso de adopción"
        "En proceso de devolucion" -> "En proceso de devolución"
        "En adaptacion" -> "En adaptación"
        "Adaptacion favorable" -> "Adaptación favorable"
        "Adaptacion completada" -> "Adaptación completada"
        "Requiere atencion" -> "Requiere atención"
        null -> ""
        else -> db
    }

    enum class Tone { GOOD, WARN, BAD, NEUTRAL }

    fun tone(estado: String?): Tone = when (estado) {
        "Disponible", "Aprobada", "Aprobado", "Aceptada", "Adaptacion favorable", "Adaptacion completada", "Cargado", "Adoptada" -> Tone.GOOD
        "Pendiente", "En revision", "Documentacion pendiente", "En adaptacion", "En proceso de adopcion", "En proceso de devolucion", "En seguimiento" -> Tone.WARN
        "Rechazada", "Rechazado", "Requiere atencion", "Bloqueado" -> Tone.BAD
        else -> Tone.NEUTRAL
    }

    /** Avance mostrado en la barra de adaptación (0-100). */
    fun adaptacionProgreso(estado: String?): Int = when (estado) {
        "En adaptacion" -> 35
        "Requiere atencion" -> 50
        "Adaptacion favorable" -> 75
        "Adaptacion completada" -> 100
        else -> 5
    }

    fun mask(correo: String): String {
        val at = correo.indexOf('@')
        if (at < 1) return correo
        val user = correo.substring(0, at)
        return "*".repeat(maxOf(user.length - 3, 3)) + user.takeLast(3) + correo.substring(at)
    }
}
