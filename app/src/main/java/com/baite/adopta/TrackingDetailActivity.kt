package com.baite.adopta

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

class TrackingDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracking_detail)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val petId = intent.getIntExtra(Extras.PET_ID, -1)
        val petName = intent.getStringExtra(Extras.PET_NAME)
        SampleData.adoptadas.firstOrNull { it.id == petId }?.let {
            findViewById<TextView>(R.id.tvNombre).text = it.nombre
            findViewById<TextView>(R.id.tvFechaAdopcion).text = it.fechaAdopcion
            findViewById<TextView>(R.id.tvEstado).text = it.estado
            findViewById<LinearProgressIndicator>(R.id.progressAdaptacion).setProgressCompat(it.nivel, false)
        }

        findViewById<MaterialButton>(R.id.btnRegistrar).setOnClickListener {
            startActivity(
                Intent(this, RegisterTrackingActivity::class.java)
                    .putExtra(Extras.PET_ID, petId)
                    .putExtra(Extras.PET_NAME, petName)
            )
        }
        findViewById<MaterialButton>(R.id.btnHistorial).setOnClickListener {
            startActivity(
                Intent(this, TrackingHistoryActivity::class.java).putExtra(Extras.PET_ID, petId)
            )
        }
    }
}
