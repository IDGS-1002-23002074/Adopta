package com.baite.adopta

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()

        val goEdit = View.OnClickListener { startActivity(Intent(ctx, EditProfileActivity::class.java)) }
        view.findViewById<View>(R.id.btnEditar).setOnClickListener(goEdit)
        view.findViewById<View>(R.id.rowEditar).setOnClickListener(goEdit)
        view.findViewById<View>(R.id.rowConfig).setOnClickListener {
            startActivity(Intent(ctx, SettingsActivity::class.java))
        }
        view.findViewById<View>(R.id.rowPassword).setOnClickListener {
            startActivity(Intent(ctx, ChangePasswordActivity::class.java))
        }
        view.findViewById<View>(R.id.btnCerrarSesion).setOnClickListener {
            startActivity(
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }

        view.findViewById<TextView>(R.id.tvAdopciones).text = SampleData.adoptadas.size.toString()
        view.findViewById<TextView>(R.id.tvFavoritos).text = SampleData.favoritos.size.toString()

        val rv = view.findViewById<RecyclerView>(R.id.rvAdopciones)
        rv.layoutManager = LinearLayoutManager(ctx)
        rv.isNestedScrollingEnabled = false
        rv.adapter = AdoptedPetsAdapter(SampleData.adoptadas) { openTracking(it) }
        view.findViewById<View>(R.id.tvSinAdopciones).visibility =
            if (SampleData.adoptadas.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openTracking(p: AdoptedPet) {
        startActivity(
            Intent(requireContext(), TrackingDetailActivity::class.java)
                .putExtra(Extras.PET_ID, p.id)
                .putExtra(Extras.PET_NAME, p.nombre)
        )
    }
}
