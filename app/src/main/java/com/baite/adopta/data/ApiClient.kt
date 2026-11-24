package com.baite.adopta.data

import com.baite.adopta.BuildConfig
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    /** Servidor web donde se alojan las fotos de las mascotas subidas desde la web. */
    const val WEB_MEDIA_BASE_URL = "https://adopta-api-hagrcpb2ebhseyfy.eastus-01.azurewebsites.net/"

    /** idMascota <-> id_mascota, y se envían los nulos (para poder limpiar campos). */
    val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .serializeNulls()
        .create()

    fun baseUrl(): String {
        val url = BuildConfig.API_BASE_URL.trim()
        return if (url.endsWith("/")) url else "$url/"
    }

    /** Convierte la URL de la foto de una mascota para leerla desde el servidor web. */
    fun petImageUrl(url: String?): String? {
        if (url.isNullOrBlank()) return url
        val mobileBase = baseUrl().trimEnd('/')
        val webBase = WEB_MEDIA_BASE_URL.trimEnd('/')
        return url.replace(mobileBase, webBase)
    }

    /** Cliente HTTP compartido (también lo usa Coil para las imágenes). */
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request()
                val builder = req.newBuilder()
                // El token solo viaja hacia nuestro servidor, nunca a otros hosts.
                val ownHost = baseUrl().toHttpUrlOrNull()?.host
                val token = Session.token
                if (token != null && req.url.host == ownHost) {
                    builder.header("Authorization", "Bearer $token")
                }
                chain.proceed(builder.build())
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
            }
            .build()
    }

    @Volatile private var service: ApiService? = null

    val api: ApiService
        get() = service ?: synchronized(this) {
            service ?: Retrofit.Builder()
                .baseUrl(baseUrl())
                .client(http)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(ApiService::class.java)
                .also { service = it }
        }

}