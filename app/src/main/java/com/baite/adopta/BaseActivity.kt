package com.baite.adopta

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.baite.adopta.data.AppEvents
import kotlinx.coroutines.launch

/**
 * Base de todas las pantallas:
 *  - respeta las barras del sistema (edge-to-edge),
 *  - si el servidor indica que la sesión expiró (RNF016), regresa al inicio de sesión.
 */
abstract class BaseActivity : AppCompatActivity() {

    /** Pantallas con foto a sangre (detalle de mascota) ponen false y manejan los insets ellas mismas. */
    protected open val applyInsets: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppEvents.sessionExpired.collect { goToLogin("Tu sesión expiró. Inicia sesión de nuevo.") }
            }
        }
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        if (applyInsets) {
            val content = findViewById<ViewGroup>(android.R.id.content)
            ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                WindowInsetsCompat.CONSUMED
            }
        }
    }

    fun goToLogin(message: String? = null) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(Extras.MESSAGE, message)
        )
        finish()
    }
}
