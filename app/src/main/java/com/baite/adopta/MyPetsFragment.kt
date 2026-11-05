package com.baite.adopta

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MyPetsFragment : Fragment(R.layout.fragment_my_pets) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rvMisMascotas)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.layoutAnimation = AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_fade_up)
        rv.adapter = AdoptedPetsAdapter(SampleData.adoptadas) { p ->
            startActivity(
                Intent(requireContext(), TrackingDetailActivity::class.java)
                    .putExtra(Extras.PET_ID, p.id)
                    .putExtra(Extras.PET_NAME, p.nombre)
            )
        }
        view.findViewById<View>(R.id.layoutEmpty).visibility =
            if (SampleData.adoptadas.isEmpty()) View.VISIBLE else View.GONE
    }
}
