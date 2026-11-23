package com.baite.adopta

import org.json.JSONArray
import org.json.JSONObject

/**
 * Carga mascotas desde la API y las vuelca en [SampleData.mascotas] / [SampleData.guardadosIds],
 * que es de donde ya leen Descubrir, Guardados y el detalle.
 */
object PetRepository {

    /** Primera carga: catálogo disponible + favoritos. [onDone] recibe un mensaje si hubo error. */
    fun load(onDone: (error: String?) -> Unit) {
        Api.call("GET", "/api/mascotas?limit=50",
            onError = { onDone(it.message) }
        ) { r ->
            val catalogo = parseList(r.optJSONArray("items"))
            Api.call("GET", "/api/favoritos",
                onError = {
                    // Sin favoritos igual mostramos el catálogo.
                    SampleData.mascotas = catalogo.map { it.first }
                    SampleData.guardadosIds.clear()
                    SampleData.guardadosIds.addAll(catalogo.filter { it.second }.map { it.first.id })
                    onDone(null)
                }
            ) { f ->
                val favoritos = parseList(f.optJSONArray("items"))
                val ids = catalogo.map { it.first.id }.toSet()
                // Un favorito que ya no está "Disponible" igual debe verse en Guardados.
                val todas = catalogo.map { it.first } + favoritos.map { it.first }.filter { it.id !in ids }
                SampleData.mascotas = todas
                SampleData.guardadosIds.clear()
                SampleData.guardadosIds.addAll(favoritos.map { it.first.id })
                onDone(null)
            }
        }
    }

    /** Detalle: descripción completa y todas las fotos. Actualiza la mascota en memoria. */
    fun loadDetail(id: Int, onDone: (Pet?) -> Unit) {
        Api.call("GET", "/api/mascotas/$id", onError = { onDone(null) }) { r ->
            val actual = SampleData.pet(id)
            if (actual == null) {
                onDone(null)
                return@call
            }
            val fotos = mutableListOf<String>()
            r.optJSONArray("fotos")?.let { a ->
                for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() && it != "null" }?.let(fotos::add)
            }
            val descripcion = listOfNotNull(r.str("descripcion"), r.str("comportamiento")).joinToString("\n\n")
            val actualizada = actual.copy(
                descripcion = descripcion.ifBlank { actual.descripcion },
                fotos = fotos,
                foto = fotos.firstOrNull() ?: actual.foto
            )
            SampleData.mascotas = SampleData.mascotas.map { if (it.id == id) actualizada else it }
            onDone(actualizada)
        }
    }

    /** Guarda / quita un favorito en el servidor (el estado local ya se actualizó antes). */
    fun setFavorite(id: Int, saved: Boolean) {
        Api.call(if (saved) "POST" else "DELETE", "/api/favoritos/$id", onError = {}) { }
    }

    private fun parseList(arr: JSONArray?): List<Pair<Pet, Boolean>> {
        if (arr == null) return emptyList()
        val out = mutableListOf<Pair<Pet, Boolean>>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (!o.has("id_mascota")) continue
            out += parse(o, i) to o.optBoolean("es_favorito", false)
        }
        return out
    }

    private fun parse(o: JSONObject, index: Int): Pet {
        val tamano = when (o.str("tamano")) {
            "Pequeno" -> "Pequeño"
            null -> "—"
            else -> o.str("tamano")!!
        }
        return Pet(
            id = o.getInt("id_mascota"),
            nombre = o.str("nombre") ?: "Sin nombre",
            raza = o.str("raza") ?: "Mestizo",
            ubicacion = o.str("ciudad") ?: "",
            especie = o.str("especie") ?: "Perro",
            edad = o.str("edad_aproximada") ?: "—",
            sexo = o.str("sexo") ?: "—",
            tamano = tamano,
            energia = "—",
            distanciaKm = -1.0, // la API aún no calcula distancia
            refugio = o.str("organizacion") ?: "",
            respuesta = "",
            vacunada = o.optBoolean("vacunado", false),
            esterilizada = o.optBoolean("esterilizado", false),
            tono = index,
            foto = o.str("foto_principal")
        )
    }
}
