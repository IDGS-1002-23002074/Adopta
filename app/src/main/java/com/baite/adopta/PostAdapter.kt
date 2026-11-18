package com.baite.adopta

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.baite.adopta.data.Format
import com.baite.adopta.data.Publicacion

/** Pinta una publicación (se usa en el feed y en el detalle). */
object PostBinder {
    fun bind(
        v: View, p: Publicacion,
        onLike: (Publicacion) -> Unit, onOpen: ((Publicacion) -> Unit)?, onMenu: (Publicacion, View) -> Unit
    ) {
        v.findViewById<ImageView>(R.id.ivAvatar).loadAvatar(p.autor.foto)
        v.findViewById<TextView>(R.id.tvAutor).text = p.autor.nombre
        v.findViewById<TextView>(R.id.tvFecha).text = Format.relative(p.fechaPublicacion) + if (p.editada) " · editada" else ""
        v.findViewById<TextView>(R.id.tvContenido).text = p.contenido
        v.findViewById<ImageView>(R.id.ivImagen).apply {
            visible(!p.imagen.isNullOrBlank())
            if (!p.imagen.isNullOrBlank()) loadPhoto(p.imagen, p.idPublicacion)
        }
        v.findViewById<ImageButton>(R.id.btnLike).apply {
            setImageResource(if (p.meGusta) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
            setColorFilter(androidx.core.content.ContextCompat.getColor(context, if (p.meGusta) R.color.adopta_danger else R.color.adopta_gray))
            contentDescription = if (p.meGusta) "Quitar Me gusta" else "Me gusta"
            setOnClickListener { onLike(p) }
        }
        v.findViewById<TextView>(R.id.tvLikes).text = p.likes.toString()
        v.findViewById<TextView>(R.id.tvComentarios).text = p.comentarios.toString()
        v.findViewById<View>(R.id.btnComentar).setOnClickListener { onOpen?.invoke(p) }
        v.findViewById<View>(R.id.btnMas).apply {
            visible(p.esMia)
            setOnClickListener { onMenu(p, this) }
        }
        if (onOpen != null) v.setOnClickListener { onOpen(p) }
    }
}

class PostAdapter(
    private val onLike: (Publicacion) -> Unit,
    private val onOpen: (Publicacion) -> Unit,
    private val onMenu: (Publicacion, View) -> Unit
) : RecyclerView.Adapter<PostAdapter.VH>() {

    private var items: List<Publicacion> = emptyList()
    class VH(v: View) : RecyclerView.ViewHolder(v)

    fun submit(list: List<Publicacion>) {
        val old = items
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = list.size
            override fun areItemsTheSame(o: Int, n: Int) = old[o].idPublicacion == list[n].idPublicacion
            override fun areContentsTheSame(o: Int, n: Int) = old[o] == list[n]
        })
        items = list
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_post, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) = PostBinder.bind(h.itemView, items[position], onLike, onOpen, onMenu)
    override fun getItemCount() = items.size
}
