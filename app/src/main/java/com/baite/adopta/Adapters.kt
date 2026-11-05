package com.baite.adopta

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator

class AdoptedPetsAdapter(
    private val items: List<AdoptedPet>,
    private val onSeguimiento: (AdoptedPet) -> Unit
) : RecyclerView.Adapter<AdoptedPetsAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_adopted_pet, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val p = items[position]
        val v = h.itemView
        v.findViewById<TextView>(R.id.tvNombre).text = p.nombre
        v.findViewById<TextView>(R.id.tvFechaAdopcion).text = p.fechaAdopcion
        v.findViewById<TextView>(R.id.tvEstado).text = p.estado
        v.findViewById<TextView>(R.id.tvUltimoSeguimiento).text = p.ultimo
        v.findViewById<TextView>(R.id.tvNivel).text = "${p.nivel}%"
        v.findViewById<LinearProgressIndicator>(R.id.progressAdaptacion).setProgressCompat(p.nivel, true)
        v.findViewById<View>(R.id.btnVerSeguimiento).setOnClickListener { onSeguimiento(p) }
    }

    override fun getItemCount() = items.size
}

class FavoritesAdapter(
    items: List<Pet>,
    private val onClick: (Pet) -> Unit,
    private val onUnsave: (Pet) -> Unit
) : RecyclerView.Adapter<FavoritesAdapter.VH>() {

    private var items: List<Pet> = items

    class VH(v: View) : RecyclerView.ViewHolder(v)

    fun submit(list: List<Pet>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_favorite, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val p = items[position]
        val v = h.itemView
        PetPhoto.bind(v.findViewById<ImageView>(R.id.ivMascota), p)
        v.findViewById<TextView>(R.id.tvNombre).text = p.nombre
        v.findViewById<TextView>(R.id.tvRaza).text = "${p.raza} · ${p.edad}"
        v.findViewById<TextView>(R.id.tvUbicacion).text = p.ubicacion
        v.setOnClickListener { onClick(p) }
        v.findViewById<View>(R.id.btnFavorito).setOnClickListener { onUnsave(p) }
    }

    override fun getItemCount() = items.size
}

class NotificationsAdapter(
    private val all: List<AppNotification>,
    private val onClick: (AppNotification) -> Unit
) : RecyclerView.Adapter<NotificationsAdapter.VH>() {

    private var shown: List<AppNotification> = all

    class VH(v: View) : RecyclerView.ViewHolder(v)

    fun filter(tipo: String?) {
        shown = if (tipo == null) all else all.filter { it.tipo == tipo }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val n = shown[position]
        val v = h.itemView
        v.findViewById<TextView>(R.id.tvTitulo).text = n.titulo
        v.findViewById<TextView>(R.id.tvMensaje).text = n.mensaje
        v.findViewById<TextView>(R.id.tvTiempo).text = n.tiempo
        v.findViewById<View>(R.id.viewNoLeida).visibility = if (n.leida) View.GONE else View.VISIBLE
        (v as MaterialCardView).setCardBackgroundColor(
            ContextCompat.getColor(v.context, if (n.leida) R.color.white else R.color.adopta_cream)
        )
        v.setOnClickListener { onClick(n) }
    }

    override fun getItemCount() = shown.size
}
