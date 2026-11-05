package com.baite.adopta

/** Datos de ejemplo para poder navegar entre pantallas. Reemplazar por la fuente real (API/BD). */
data class Pet(
    val id: Int,
    val nombre: String,
    val raza: String,
    val ubicacion: String,
    val especie: String = "Perro",
    val edad: String = "2 años",
    val sexo: String = "Hembra",
    val tamano: String = "Mediano",
    val energia: String = "Media",
    val distanciaKm: Double = 3.0,
    val descripcion: String = "",
    val refugio: String = "Refugio Huellitas",
    val respuesta: String = "Responde en ~2 h",
    val personalidad: List<String> = emptyList(),
    val vacunada: Boolean = true,
    val esterilizada: Boolean = true,
    val compatibilidad: Int = 0,
    val razonCompat: String = "",
    /** DEMO: simula que el refugio acepta el interés (momento match). Sustituir por la respuesta del backend. */
    val aceptaInteres: Boolean = false,
    /** Índice de color del placeholder mientras no haya foto real (ver PetPhoto). */
    val tono: Int = 0
)

data class AdoptedPet(
    val id: Int,
    val nombre: String,
    val fechaAdopcion: String,
    val estado: String,
    val nivel: Int,
    val ultimo: String
)

data class AppNotification(
    val id: Int,
    val titulo: String,
    val mensaje: String,
    val tiempo: String,
    val tipo: String,
    val leida: Boolean
)

object SampleData {

    const val MAX_SOLICITUDES = 3

    val mascotas = listOf(
        Pet(1, "Luna", "Mestiza", "León, Gto.", especie = "Gato", edad = "2 años", sexo = "Hembra",
            tamano = "Mediana", energia = "Media", distanciaKm = 3.2,
            descripcion = "Luna es cariñosa, juguetona y se lleva bien con otros gatos. Busca un hogar tranquilo donde pueda sentirse segura.",
            personalidad = listOf("Cariñosa", "Tranquila", "Juguetona"), compatibilidad = 92,
            razonCompat = "Buscas un hogar tranquilo y Luna prefiere los espacios calmados.", tono = 0),
        Pet(2, "Max", "Labrador", "Guanajuato, Gto.", edad = "3 años", sexo = "Macho",
            tamano = "Grande", energia = "Alta", distanciaKm = 18.5,
            descripcion = "Max adora salir a correr y es muy sociable con niños. Necesita espacio y paseos diarios.",
            refugio = "Casa Peluda", personalidad = listOf("Activo", "Sociable", "Leal"), compatibilidad = 84,
            razonCompat = "Tienes tiempo para paseos y Max necesita actividad diaria.", tono = 1),
        Pet(3, "Miel", "Siamés", "Irapuato, Gto.", especie = "Gato", edad = "1 año", sexo = "Hembra",
            tamano = "Pequeña", energia = "Media", distanciaKm = 32.0,
            descripcion = "Miel es curiosa y muy comunicativa. Le encanta trepar y dormir al sol.",
            refugio = "Patitas Felices", personalidad = listOf("Curiosa", "Hablantina"), compatibilidad = 78,
            razonCompat = "Vives en un lugar con ventanas amplias, ideal para su curiosidad.", tono = 2),
        Pet(4, "Nala", "Pastor mestizo", "Silao, Gto.", edad = "1 año", sexo = "Hembra",
            tamano = "Grande", energia = "Alta", distanciaKm = 4.1,
            descripcion = "Nala es inteligente y aprende rápido. Busca una familia que disfrute del aire libre.",
            personalidad = listOf("Inteligente", "Activa", "Protectora"), compatibilidad = 88,
            razonCompat = "Tienes patio y Nala necesita espacio para jugar.", aceptaInteres = true, tono = 3),
        Pet(5, "Rocky", "Beagle", "León, Gto.", edad = "4 años", sexo = "Macho",
            tamano = "Mediano", energia = "Media", distanciaKm = 2.4,
            descripcion = "Rocky es un explorador nato con una nariz increíble. Tranquilo en casa, feliz en el parque.",
            refugio = "Casa Peluda", personalidad = listOf("Curioso", "Dulce", "Tranquilo"), compatibilidad = 90,
            razonCompat = "Su ritmo tranquilo encaja con tu rutina.", tono = 1),
        Pet(6, "Coco", "Schnauzer", "Romita, Gto.", edad = "3 años", sexo = "Macho",
            tamano = "Pequeño", energia = "Baja", distanciaKm = 12.8,
            descripcion = "Coco es un compañero ideal para departamento: silencioso, limpio y muy apegado.",
            refugio = "Patitas Felices", personalidad = listOf("Apegado", "Silencioso"), compatibilidad = 81,
            razonCompat = "Es ideal para espacios pequeños.", tono = 2),
        Pet(7, "Pelusa", "Angora mestiza", "León, Gto.", especie = "Gato", edad = "8 meses", sexo = "Hembra",
            tamano = "Pequeña", energia = "Alta", distanciaKm = 5.6,
            descripcion = "Pelusa es una gatita llena de energía que convierte todo en juguete.",
            personalidad = listOf("Juguetona", "Traviesa", "Cariñosa"), compatibilidad = 76,
            razonCompat = "Pelusa necesita compañía y juego diario.", tono = 0),
        Pet(8, "Thor", "Husky mestizo", "Guanajuato, Gto.", edad = "2 años", sexo = "Macho",
            tamano = "Grande", energia = "Alta", distanciaKm = 21.3,
            descripcion = "Thor tiene energía de sobra y mucha personalidad. Ideal para quien disfruta del senderismo.",
            refugio = "Casa Peluda", personalidad = listOf("Aventurero", "Vocal", "Leal"), compatibilidad = 72,
            razonCompat = "Requiere mucha actividad; revisa si se ajusta a tu tiempo libre.", tono = 3)
    )

    val adoptadas = listOf(
        AdoptedPet(1, "Toby", "Adoptado el 12 oct 2026", "En adaptación", 60, "Última actualización: hace 2 días")
    )
    val notificaciones = listOf(
        AppNotification(1, "Solicitud aprobada", "Tu solicitud de adopción para Luna fue aprobada.", "Hace 1 h", "Solicitudes", false),
        AppNotification(2, "Registra el avance de Toby", "Cuéntanos cómo va su adaptación.", "Ayer", "Seguimiento", true),
        AppNotification(3, "Nuevas mascotas", "Hay 5 mascotas nuevas cerca de ti.", "Hace 3 días", "Comunidad", true)
    )

    // Estado en memoria (DEMO). Mover a base de datos / API.
    val guardadosIds: MutableSet<Int> = mutableSetOf(1, 2, 3)
    val intereses: MutableSet<Int> = mutableSetOf()
    val decididos: MutableSet<Int> = mutableSetOf()
    val solicitudes: MutableSet<Int> = mutableSetOf()

    val favoritos: List<Pet>
        get() = mascotas.filter { it.id in guardadosIds }

    fun pet(id: Int): Pet? = mascotas.firstOrNull { it.id == id }

    /** Mazo del día: lo que aún no guardaste ni decidiste. Finito a propósito. */
    fun deck(): List<Pet> = mascotas.filter { it.id !in guardadosIds && it.id !in decididos }

    fun toggleGuardado(id: Int): Boolean {
        return if (id in guardadosIds) {
            guardadosIds.remove(id); false
        } else {
            guardadosIds.add(id); true
        }
    }
}
