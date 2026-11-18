package com.baite.adopta

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.Session

class AdoptaApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        Session.init(this)
    }

    /** Coil usa el mismo cliente HTTP (para documentos protegidos) y guarda las fotos en disco. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { ApiClient.http }
        .diskCache {
            DiskCache.Builder().directory(cacheDir.resolve("imagenes")).maxSizeBytes(64L * 1024 * 1024).build()
        }
        .crossfade(true)
        .build()
}
