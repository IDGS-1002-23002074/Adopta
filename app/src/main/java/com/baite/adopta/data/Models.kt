package com.baite.adopta.data

/**
 * Modelos de la API (backend/). Los nombres en camelCase se convierten solos a snake_case
 * (idMascota <-> id_mascota). Todos los campos tienen valor por defecto para que Gson
 * no deje nulos inesperados.
 */

// ---------------------------------------------------------------- acceso y perfil
data class Usuario(
    val idUsuario: Int = 0,
    val nombre: String = "",
    val apellidoPaterno: String = "",
    val apellidoMaterno: String? = null,
    val correo: String = "",
    val telefono: String? = null,
    val fotoPerfil: String? = null,
    val rol: String = "",
    val fechaRegistro: String? = null
) {
    val nombreCompleto: String get() = listOfNotNull(nombre, apellidoPaterno, apellidoMaterno).filter { it.isNotBlank() }.joinToString(" ")
    val iniciales: String get() = (nombre.take(1) + apellidoPaterno.take(1)).uppercase()
}

data class AuthResponse(val token: String = "", val usuario: Usuario = Usuario())
data class MensajeResponse(val mensaje: String = "")
data class ForgotResponse(
    val mensaje: String = "",
    val correoEnmascarado: String = "",
    val vigenciaMinutos: Int = 10,
    val codigoDev: String? = null
)

data class PerfilAdoptante(
    val fechaNacimiento: String? = null,
    val direccion: String? = null,
    val ciudad: String? = null,
    val codigoPostal: String? = null,
    val tipoVivienda: String? = null,
    val tieneMascotas: Boolean = false,
    val experienciaMascotas: String? = null,
    val ocupacion: String? = null,
    val contactoEmergenciaNombre: String? = null,
    val contactoEmergenciaTelefono: String? = null,
    val contactoEmergenciaParentesco: String? = null
)

data class Preferencias(val especie: String? = null, val tamano: String? = null, val edadPreferida: String? = null)
data class Resumen(val favoritos: Int = 0, val adopciones: Int = 0, val solicitudesActivas: Int = 0)
data class MeResponse(
    val usuario: Usuario = Usuario(),
    val perfil: PerfilAdoptante = PerfilAdoptante(),
    val preferencias: Preferencias = Preferencias(),
    val resumen: Resumen = Resumen()
)
data class MeUpdate(val usuario: Map<String, String?>, val perfil: Map<String, Any?>)
data class FotoResponse(val fotoPerfil: String? = null)

// ---------------------------------------------------------------- catálogo
data class Motivo(val idMotivo: Int = 0, val nombre: String = "")
data class Catalogos(
    val especies: List<String> = emptyList(),
    val razas: List<String> = emptyList(),
    val ciudades: List<String> = emptyList(),
    val tamanos: List<String> = emptyList(),
    val sexos: List<String> = emptyList(),
    val motivosDevolucion: List<Motivo> = emptyList()
)

data class Pet(
    val idMascota: Int = 0,
    val nombre: String = "",
    val especie: String = "",
    val raza: String? = null,
    val sexo: String = "",
    val edadAproximada: String? = null,
    val tamano: String? = null,
    val color: String? = null,
    val vacunado: Boolean = false,
    val esterilizado: Boolean = false,
    val estadoMascota: String = "",
    val idOrganizacion: Int = 0,
    val organizacion: String = "",
    val ciudad: String? = null,
    val organizacionLogo: String? = null,
    val fotoPrincipal: String? = null,
    val esFavorito: Boolean = false
)
data class PetPage(val total: Int = 0, val items: List<Pet> = emptyList())

data class OrgDetalle(
    val idOrganizacion: Int = 0,
    val nombre: String = "",
    val descripcion: String? = null,
    val direccion: String? = null,
    val ciudad: String? = null,
    val telefono: String? = null,
    val correoContacto: String? = null,
    val sitioWeb: String? = null,
    val logo: String? = null
)
data class MiSolicitud(val idSolicitud: Int = 0, val estado: String = "")
data class PetDetail(
    val idMascota: Int = 0,
    val nombre: String = "",
    val especie: String = "",
    val raza: String? = null,
    val sexo: String = "",
    val edadAproximada: String? = null,
    val tamano: String? = null,
    val color: String? = null,
    val vacunado: Boolean = false,
    val esterilizado: Boolean = false,
    val estadoMascota: String = "",
    val ciudad: String? = null,
    val esFavorito: Boolean = false,
    val descripcion: String? = null,
    val comportamiento: String? = null,
    val estadoSalud: String? = null,
    val peso: Double? = null,
    val fotos: List<String> = emptyList(),
    val organizacionDetalle: OrgDetalle = OrgDetalle(),
    val miSolicitud: MiSolicitud? = null
)
data class FavoritoResponse(val esFavorito: Boolean = false)

// ---------------------------------------------------------------- solicitudes
data class SolicitudItem(
    val idSolicitud: Int = 0,
    val fechaSolicitud: String = "",
    val estado: String = "",
    val idMascota: Int = 0,
    val mascota: String = "",
    val especie: String = "",
    val organizacion: String = "",
    val fotoMascota: String? = null,
    val fechaLimiteDocumentos: String? = null,
    val documentacionCompleta: Boolean = false,
    val requiereDocumentos: Boolean = false,
    val puedeCancelar: Boolean = false
)
data class SolicitudList(val items: List<SolicitudItem> = emptyList(), val activas: Int = 0, val maximoActivas: Int = 3)

data class NuevaSolicitud(
    val idMascota: Int,
    val motivoAdopcion: String,
    val experienciaPrevia: String?,
    val condicionesVivienda: String,
    val aceptaCompromisos: Boolean
)

data class HistorialItem(val idHistorial: Int = 0, val estado: String = "", val observacion: String? = null, val fechaCambio: String = "")
data class Archivo(
    val idArchivo: Int = 0,
    val nombreOriginal: String = "",
    val extension: String = "",
    val tamanoBytes: Long = 0,
    val fechaEmisionDocumento: String? = null,
    val fechaCarga: String = "",
    val url: String = ""
)
data class Requisito(
    val idRequisito: Int = 0,
    val clave: String = "",
    val nombre: String = "",
    val descripcion: String? = null,
    val obligatorio: Boolean = true,
    val estado: String = "Pendiente",
    val motivoRechazo: String? = null,
    val formatosPermitidos: String = "pdf,jpg,png",
    val tamanoMaxMb: Int = 5,
    val minArchivos: Int = 1,
    val maxArchivos: Int = 1,
    val requiereFechaEmision: Boolean = false,
    val vigenciaMeses: Int? = null,
    val archivos: List<Archivo> = emptyList(),
    val cumple: Boolean = false
)
data class Referencia(
    val idReferencia: Int = 0,
    val nombreCompleto: String = "",
    val telefono: String = "",
    val relacion: String = "",
    val aniosConocidos: Int? = null
)
data class NuevaReferencia(val nombreCompleto: String, val telefono: String, val relacion: String, val aniosConocidos: Int?)
data class Pendientes(val documentos: Int = 0, val informacion: Boolean = false, val referencias: Int = 0)

/** Formulario "Información complementaria" (1:1 con la solicitud). */
data class InfoHogar(
    val regimenVivienda: String? = null,
    val arrendadorPermiteMascotas: Boolean? = null,
    val tieneEspacioExterior: Boolean? = null,
    val espacioExteriorCercado: Boolean? = null,
    val superficieM2: Int? = null,
    val numPersonasHogar: Int? = null,
    val hayMenores: Boolean? = null,
    val hogarDeAcuerdo: Boolean? = null,
    val horasSoloDia: String? = null,
    val lugarDormir: String? = null,
    val cuidadorAlterno: String? = null,
    val presupuestoMensual: String? = null,
    val tieneOtrasMascotas: Boolean? = null,
    val cantidadOtrasMascotas: Int? = null,
    val especiesOtrasMascotas: String? = null,
    val otrasEsterilizadas: Boolean? = null,
    val otrasVacunadas: Boolean? = null,
    val historialMascotasPrevias: String? = null,
    val veterinarioNombre: String? = null,
    val veterinarioTelefono: String? = null,
    val aceptaVisitaDomiciliaria: Boolean? = null,
    val aceptaSeguimiento: Boolean? = null,
    val compromisoEsterilizacion: Boolean? = null,
    val aceptaAvisoPrivacidad: Boolean? = null,
    val declaracionVeracidad: Boolean? = null,
    val firmaNombre: String? = null,
    val estadoFormulario: String? = null
)

data class SolicitudDetalle(
    val idSolicitud: Int = 0,
    val fechaSolicitud: String = "",
    val estado: String = "",
    val idMascota: Int = 0,
    val mascota: String = "",
    val fotoMascota: String? = null,
    val organizacion: String = "",
    val motivoAdopcion: String? = null,
    val experienciaPrevia: String? = null,
    val condicionesVivienda: String? = null,
    val observacionesOrganizacion: String? = null,
    val fechaResolucion: String? = null,
    val fechaLimiteDocumentos: String? = null,
    val fechaEnvioDocumentos: String? = null,
    val documentacionCompleta: Boolean = false,
    val puedeCancelar: Boolean = false,
    val puedeEditarDocumentos: Boolean = false,
    val puedeEnviar: Boolean = false,
    val pendientes: Pendientes = Pendientes(),
    val historial: List<HistorialItem> = emptyList(),
    val requisitos: List<Requisito> = emptyList(),
    val informacion: InfoHogar? = null,
    val referencias: List<Referencia> = emptyList()
)

// ---------------------------------------------------------------- mi hogar / seguimiento / devoluciones
data class AdopcionItem(
    val idAdopcion: Int = 0,
    val fechaAdopcion: String = "",
    val idSolicitud: Int = 0,
    val idMascota: Int = 0,
    val nombre: String = "",
    val especie: String = "",
    val raza: String? = null,
    val sexo: String = "",
    val edadAproximada: String? = null,
    val tamano: String? = null,
    val organizacion: String = "",
    val foto: String? = null,
    val estadoMascota: String = "",
    val estadoAdaptacion: String = "",
    val totalReportes: Int = 0,
    val ultimoReporte: String? = null,
    val estadoDevolucion: String? = null
)
data class AdopcionList(val items: List<AdopcionItem> = emptyList())

data class Reporte(
    val idReporte: Int = 0,
    val comportamiento: String? = null,
    val salud: String? = null,
    val alimentacion: String? = null,
    val adaptacion: String? = null,
    val observaciones: String? = null,
    val fechaReporte: String = "",
    val fotos: List<String> = emptyList()
)
data class CambioAdaptacion(
    val idHistorialAdaptacion: Int = 0,
    val estado: String = "",
    val observaciones: String? = null,
    val fechaCambio: String = "",
    val responsable: String = "",
    val rol: String = ""
)
data class DevolucionResumen(
    val idDevolucion: Int = 0,
    val estado: String = "",
    val explicacion: String = "",
    val fechaSolicitud: String = "",
    val fechaRevision: String? = null,
    val observacionesOrganizacion: String? = null
)
data class AdopcionDetalle(
    val idAdopcion: Int = 0,
    val fechaAdopcion: String = "",
    val idMascota: Int = 0,
    val nombre: String = "",
    val especie: String = "",
    val raza: String? = null,
    val sexo: String = "",
    val edadAproximada: String? = null,
    val tamano: String? = null,
    val organizacion: String = "",
    val foto: String? = null,
    val estadoMascota: String = "",
    val estadoAdaptacion: String = "",
    val totalReportes: Int = 0,
    val observaciones: String? = null,
    val reportes: List<Reporte> = emptyList(),
    val historialAdaptacion: List<CambioAdaptacion> = emptyList(),
    val devolucion: DevolucionResumen? = null,
    val puedeReportar: Boolean = true,
    val puedeDevolver: Boolean = true
)
data class IdReporte(val idReporte: Int = 0)

data class NuevaDevolucion(val idAdopcion: Int, val motivos: List<Int>, val explicacion: String)
data class Devolucion(
    val idDevolucion: Int = 0,
    val idAdopcion: Int = 0,
    val estado: String = "",
    val explicacion: String = "",
    val fechaSolicitud: String = "",
    val fechaRevision: String? = null,
    val observacionesOrganizacion: String? = null,
    val idMascota: Int = 0,
    val mascota: String = "",
    val especie: String = "",
    val organizacion: String = "",
    val foto: String? = null,
    val motivos: List<String> = emptyList()
)
data class DevolucionList(val items: List<Devolucion> = emptyList())

// ---------------------------------------------------------------- comunidad
data class Autor(val idUsuario: Int = 0, val nombre: String = "", val foto: String? = null, val rol: String = "")
data class Publicacion(
    val idPublicacion: Int = 0,
    val contenido: String = "",
    val imagen: String? = null,
    val fechaPublicacion: String = "",
    val editada: Boolean = false,
    val autor: Autor = Autor(),
    val likes: Int = 0,
    val comentarios: Int = 0,
    val meGusta: Boolean = false,
    val esMia: Boolean = false
)
data class PublicacionList(val items: List<Publicacion> = emptyList())
data class LikeResponse(val meGusta: Boolean = false, val likes: Int = 0)
data class Comentario(
    val idComentario: Int = 0,
    val idPublicacion: Int = 0,
    val contenido: String = "",
    val fechaComentario: String = "",
    val autor: Autor = Autor(),
    val esMio: Boolean = false
)
data class ComentarioList(val items: List<Comentario> = emptyList())
data class ContenidoReq(val contenido: String)

// ---------------------------------------------------------------- notificaciones
data class Notificacion(
    val idNotificacion: Int = 0,
    val titulo: String = "",
    val mensaje: String = "",
    val tipo: String? = null,
    val leida: Boolean = false,
    val fechaEnvio: String = "",
    val idSolicitud: Int? = null,
    val estadoSolicitud: String? = null
)
data class NotificacionList(val items: List<Notificacion> = emptyList(), val noLeidas: Int = 0)
data class TotalResponse(val total: Int = 0)
