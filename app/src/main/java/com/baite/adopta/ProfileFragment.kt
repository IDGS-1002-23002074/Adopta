package com.baite.adopta

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.MeResponse
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.Session
import com.baite.adopta.data.api
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** RF004-RF006 — Perfil: resumen de la cuenta, accesos y cierre de sesión. */
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        Session.user?.let { bind(it.nombreCompleto, it.correo, it.fotoPerfil, it.iniciales) }
        view.findViewById<TextView>(R.id.tvVersion).text = "Adopta+ ${BuildConfig.VERSION_NAME}"

        view.findViewById<View>(R.id.rowEditar).setOnClickListener { startActivity(Intent(ctx, EditProfileActivity::class.java)) }
        view.findViewById<View>(R.id.rowPreferencias).setOnClickListener { startActivity(Intent(ctx, SettingsActivity::class.java)) }
        view.findViewById<View>(R.id.rowPassword).setOnClickListener { startActivity(Intent(ctx, ChangePasswordActivity::class.java)) }
        view.findViewById<View>(R.id.rowSalir).setOnClickListener { confirmLogout() }
        view.findViewById<View>(R.id.statGuardados).setOnClickListener { startActivity(Intent(ctx, FavoritesActivity::class.java)) }
        view.findViewById<View>(R.id.statSolicitudes).setOnClickListener { (activity as? HomeActivity)?.openHome(MyHomeFragment.SECTION_SOLICITUDES) }
        view.findViewById<View>(R.id.statAdopciones).setOnClickListener { (activity as? HomeActivity)?.openHome(MyHomeFragment.SECTION_MASCOTAS) }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && view != null) load()
    }

    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val r = api { ApiClient.api.me() }) {
                is Outcome.Ok -> {
                    val me: MeResponse = r.value
                    Session.updateUser(me.usuario)
                    bind(me.usuario.nombreCompleto, me.usuario.correo, me.usuario.fotoPerfil, me.usuario.iniciales)
                    requireView().apply {
                        findViewById<TextView>(R.id.statGuardadosNum).text = me.resumen.favoritos.toString()
                        findViewById<TextView>(R.id.statSolicitudesNum).text = me.resumen.solicitudesActivas.toString()
                        findViewById<TextView>(R.id.statAdopcionesNum).text = me.resumen.adopciones.toString()
                    }
                }
                is Outcome.Fail -> if (r.status != 401) requireView().showFail(r) { load() }
            }
        }
    }

    private fun bind(nombre: String, correo: String, foto: String?, iniciales: String) {
        val v = view ?: return
        v.findViewById<TextView>(R.id.tvNombre).text = nombre
        v.findViewById<TextView>(R.id.tvCorreo).text = correo
        val iv = v.findViewById<ImageView>(R.id.ivAvatar)
        val tv = v.findViewById<TextView>(R.id.tvIniciales)
        if (foto.isNullOrBlank()) {
            iv.setImageDrawable(null)
            iv.setBackgroundResource(R.color.adopta_blue_light)
            tv.visible(true)
            tv.text = iniciales
        } else {
            tv.visible(false)
            iv.loadAvatar(foto)
        }
    }

    /** RF006 — Cerrar sesión: borra el token local. */
    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("¿Cerrar sesión?")
            .setMessage("Tendrás que iniciar sesión de nuevo para ver tus solicitudes y mascotas.")
            .setPositiveButton("Cerrar sesión") { _, _ ->
                Session.clear()
                (activity as? BaseActivity)?.goToLogin()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
