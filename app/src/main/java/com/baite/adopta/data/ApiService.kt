package com.baite.adopta.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Contrato con el backend (carpeta backend/). Cada método corresponde a un requerimiento RFxxx. */
interface ApiService {

    // ---- Acceso y cuenta (RF001-RF006)
    @POST("api/auth/login")
    suspend fun login(@Body body: Map<String, String>): AuthResponse

    @POST("api/auth/register")
    suspend fun register(@Body body: Map<String, String?>): RegistroResponse

    @POST("api/auth/verify-email")
    suspend fun verifyEmail(@Body body: Map<String, String>): MensajeResponse

    @POST("api/auth/resend-verification")
    suspend fun resendVerification(@Body body: Map<String, String>): RegistroResponse

    @POST("api/auth/forgot")
    suspend fun forgot(@Body body: Map<String, String>): ForgotResponse

    @POST("api/auth/reset")
    suspend fun reset(@Body body: Map<String, String>): MensajeResponse

    @GET("api/me") suspend fun me(): MeResponse
    @PUT("api/me") suspend fun updateMe(@Body body: MeUpdate): MeResponse
    @PUT("api/me/password") suspend fun changePassword(@Body body: Map<String, String>): MensajeResponse
    @Multipart @POST("api/me/foto")
    suspend fun uploadFoto(@Part foto: MultipartBody.Part): FotoResponse
    @GET("api/me/preferencias") suspend fun preferencias(): Preferencias
    @PUT("api/me/preferencias") suspend fun updatePreferencias(@Body body: Preferencias): Preferencias

    // ---- Catálogo (RF015-RF017) y favoritos (RF019)
    @GET("api/catalogos") suspend fun catalogos(): Catalogos
    @GET("api/mascotas") suspend fun mascotas(
        @Query("q") q: String? = null,
        @Query("especie") especie: String? = null,
        @Query("raza") raza: String? = null,
        @Query("tamano") tamano: String? = null,
        @Query("sexo") sexo: String? = null,
        @Query("ciudad") ciudad: String? = null,
        @Query("edad") edad: Int? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): PetPage
    @GET("api/mascotas/{id}") suspend fun mascota(@Path("id") id: Int): PetDetail
    @GET("api/favoritos") suspend fun favoritos(): PetPage
    @POST("api/favoritos/{id}") suspend fun addFavorito(@Path("id") id: Int): FavoritoResponse
    @DELETE("api/favoritos/{id}") suspend fun removeFavorito(@Path("id") id: Int): FavoritoResponse

    // ---- Solicitudes de adopción y documentación (RF020, RF024-RF026)
    @GET("api/solicitudes") suspend fun solicitudes(): SolicitudList
    @POST("api/solicitudes") suspend fun crearSolicitud(@Body body: NuevaSolicitud): SolicitudDetalle
    @GET("api/solicitudes/{id}") suspend fun solicitud(@Path("id") id: Int): SolicitudDetalle
    @POST("api/solicitudes/{id}/cancelar") suspend fun cancelarSolicitud(@Path("id") id: Int, @Body body: Map<String, String>): SolicitudDetalle
    @PUT("api/solicitudes/{id}/informacion") suspend fun guardarInformacion(@Path("id") id: Int, @Body body: InfoHogar): SolicitudDetalle
    @POST("api/solicitudes/{id}/referencias") suspend fun agregarReferencia(@Path("id") id: Int, @Body body: NuevaReferencia): SolicitudDetalle
    @DELETE("api/solicitudes/{id}/referencias/{idRef}") suspend fun quitarReferencia(@Path("id") id: Int, @Path("idRef") idRef: Int): SolicitudDetalle
    @Multipart @POST("api/solicitudes/{id}/requisitos/{idReq}/archivos")
    suspend fun subirArchivo(
        @Path("id") id: Int,
        @Path("idReq") idReq: Int,
        @Part archivo: MultipartBody.Part,
        @Part("fecha_emision_documento") fechaEmision: RequestBody? = null
    ): SolicitudDetalle
    @DELETE("api/solicitudes/{id}/archivos/{idArchivo}") suspend fun quitarArchivo(@Path("id") id: Int, @Path("idArchivo") idArchivo: Int): SolicitudDetalle
    @POST("api/solicitudes/{id}/enviar") suspend fun enviarDocumentacion(@Path("id") id: Int): SolicitudDetalle

    // ---- Mi hogar: seguimiento (RF028, RF031, RF032) y devoluciones (RF033, RF037)
    @GET("api/adopciones") suspend fun adopciones(): AdopcionList
    @GET("api/adopciones/{id}") suspend fun adopcion(@Path("id") id: Int): AdopcionDetalle
    @Multipart @POST("api/adopciones/{id}/reportes")
    suspend fun crearReporte(
        @Path("id") id: Int,
        @Part("comportamiento") comportamiento: RequestBody?,
        @Part("salud") salud: RequestBody?,
        @Part("alimentacion") alimentacion: RequestBody?,
        @Part("adaptacion") adaptacion: RequestBody?,
        @Part("observaciones") observaciones: RequestBody?,
        @Part fotos: List<MultipartBody.Part>
    ): IdReporte
    @GET("api/devoluciones") suspend fun devoluciones(): DevolucionList
    @POST("api/devoluciones") suspend fun crearDevolucion(@Body body: NuevaDevolucion): Devolucion

    // ---- Comunidad (RF038-RF042)
    @GET("api/publicaciones") suspend fun publicaciones(@Query("antes") antes: Int? = null, @Query("limit") limit: Int = 15): PublicacionList
    @GET("api/publicaciones/{id}") suspend fun publicacion(@Path("id") id: Int): Publicacion
    @Multipart @POST("api/publicaciones")
    suspend fun crearPublicacion(@Part("contenido") contenido: RequestBody, @Part imagen: MultipartBody.Part?): Publicacion
    @Multipart @PUT("api/publicaciones/{id}")
    suspend fun editarPublicacion(
        @Path("id") id: Int,
        @Part("contenido") contenido: RequestBody,
        @Part("quitar_imagen") quitarImagen: RequestBody?,
        @Part imagen: MultipartBody.Part?
    ): Publicacion
    @DELETE("api/publicaciones/{id}") suspend fun eliminarPublicacion(@Path("id") id: Int): Map<String, Boolean>
    @POST("api/publicaciones/{id}/like") suspend fun like(@Path("id") id: Int): LikeResponse
    @GET("api/publicaciones/{id}/comentarios") suspend fun comentarios(@Path("id") id: Int): ComentarioList
    @POST("api/publicaciones/{id}/comentarios") suspend fun comentar(@Path("id") id: Int, @Body body: ContenidoReq): Comentario
    @PUT("api/comentarios/{id}") suspend fun editarComentario(@Path("id") id: Int, @Body body: ContenidoReq): Comentario
    @DELETE("api/comentarios/{id}") suspend fun eliminarComentario(@Path("id") id: Int): Map<String, Boolean>

    // ---- Notificaciones (RF043)
    @GET("api/notificaciones") suspend fun notificaciones(@Query("tipo") tipo: String? = null): NotificacionList
    @GET("api/notificaciones/no-leidas") suspend fun noLeidas(): TotalResponse
    @POST("api/notificaciones/{id}/leida") suspend fun marcarLeida(@Path("id") id: Int): TotalResponse
    @POST("api/notificaciones/leer-todas") suspend fun leerTodas(): TotalResponse
}