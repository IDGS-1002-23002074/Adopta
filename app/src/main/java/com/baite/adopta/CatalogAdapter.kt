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
import com.baite.adopta.data.Pet

/** Tarjeta del catálogo: foto, estado, datos básicos, refugio y botón "Ver detalle". */
class CatalogAdapter(
    private val onClick: (Pet) -> Unit,
    private val onSave: (Pet) -> Unit
) : RecyclerView.Adapter<CatalogAdapter.VH>() {

    private var items: List<Pet> = emptyList()

    class VH(v: View) : RecyclerView.ViewHolder(v)

    fun submit(list: List<Pet>) {
        val old = items
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = list.size
            override fun areItemsTheSame(o: Int, n: Int) = old[o].idMascota == list[n].idMascota
            override fun areContentsTheSame(o: Int, n: Int) = old[o] == list[n]
        })
        items = list
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_pet_catalog, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val p = items[position]
        val v = h.itemView
        v.findViewById<ImageView>(R.id.ivMascota).loadPhoto(p.fotoPrincipal, p.idMascota)
        v.findViewById<ImageView>(R.id.ivRefugio).loadAvatar(p.organizacionLogo)
        v.findViewById<TextView>(R.id.tvNombre).text = p.nombre
        v.findViewById<TextView>(R.id.tvDetalle).text = Format.resumenMascota(p.especie, p.tamano, p.edadAproximada)
        v.findViewById<TextView>(R.id.tvRefugio).text = p.organizacion
        v.findViewById<TextView>(R.id.tvEstado).applyStatus(p.estadoMascota)
        v.setOnClickListener { onClick(p) }
        v.findViewById<View>(R.id.btnDetalle).setOnClickListener { onClick(p) }
        v.findViewById<ImageButton>(R.id.btnGuardar).apply {
            setImageResource(if (p.esFavorito) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_outline)
            contentDescription = if (p.esFavorito) "Quitar a ${p.nombre} de guardados" else "Guardar a ${p.nombre}"
            setOnClickListener { onSave(p) }
        }
    }

    override fun getItemCount() = items.size
}
