package com.baite.adopta

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

/** Galería de fotos de la mascota (se desliza de lado a lado). */
class PhotoPagerAdapter(private val urls: List<String?>, private val tono: Int) : RecyclerView.Adapter<PhotoPagerAdapter.VH>() {
    class VH(val image: ImageView) : RecyclerView.ViewHolder(image)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_photo_slide, parent, false) as ImageView)

    override fun onBindViewHolder(h: VH, position: Int) = h.image.loadPhoto(urls[position], tono)
    override fun getItemCount() = urls.size
}
