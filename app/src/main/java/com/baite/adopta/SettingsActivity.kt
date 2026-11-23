package com.baite.adopta

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var swPush: SwitchMaterial
    private lateinit var swSolicitudes: SwitchMaterial
    private lateinit var swSeguimiento: SwitchMaterial
    private lateinit var swComunidad: SwitchMaterial
    private var binding = false

    private val askPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                enablePush()
            } else {
                setSwitches()
                Toast.makeText(this, "Permite las notificaciones para recibir avisos", Toast.LENGTH_LONG).show()
                // Si ya no se puede volver a preguntar, lo único que sirve es el ajuste del sistema.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                ) openSystemNotificationSettings()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.rowEditar).setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
        findViewById<View>(R.id.rowPassword).setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnCerrarSesion).setOnClickListener { logout() }
        findViewById<TextView>(R.id.tvEliminar).setOnClickListener {
            // TODO: confirmar y eliminar la cuenta.
            Toast.makeText(this, "Función disponible próximamente", Toast.LENGTH_SHORT).show()
        }

        swPush = findViewById(R.id.swPush)
        swSolicitudes = findViewById(R.id.swSolicitudes)
        swSeguimiento = findViewById(R.id.swSeguimiento)
        swComunidad = findViewById(R.id.swComunidad)

        swPush.setOnCheckedChangeListener { _, checked -> if (!binding) onPushToggled(checked) }
        swSolicitudes.setOnCheckedChangeListener { _, c -> if (!binding) NotificationPrefs.setCategoryEnabled("Solicitudes", c) }
        swSeguimiento.setOnCheckedChangeListener { _, c -> if (!binding) NotificationPrefs.setCategoryEnabled("Seguimiento", c) }
        swComunidad.setOnCheckedChangeListener { _, c -> if (!binding) NotificationPrefs.setCategoryEnabled("Comunidad", c) }
    }

    override fun onResume() {
        super.onResume()
        // Al volver de los ajustes del sistema el permiso pudo cambiar.
        setSwitches()
    }

    /** Refleja el estado real: el interruptor solo está activo si el sistema permite avisos. */
    private fun setSwitches() {
        binding = true
        val push = NotificationPrefs.pushEnabled && NotificationManagerCompat.from(this).areNotificationsEnabled()
        swPush.isChecked = push
        swSolicitudes.isChecked = NotificationPrefs.isCategoryEnabled("Solicitudes")
        swSeguimiento.isChecked = NotificationPrefs.isCategoryEnabled("Seguimiento")
        swComunidad.isChecked = NotificationPrefs.isCategoryEnabled("Comunidad")
        listOf(swSolicitudes, swSeguimiento, swComunidad).forEach { it.isEnabled = push }
        binding = false
    }

    private fun onPushToggled(checked: Boolean) {
        if (!checked) {
            NotificationPrefs.pushEnabled = false
            NotificationWorker.cancel(this)
            setSwitches()
            return
        }
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        when {
            needsPermission -> askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            !NotificationManagerCompat.from(this).areNotificationsEnabled() -> {
                // El usuario las apagó en los ajustes del sistema.
                setSwitches()
                Toast.makeText(this, "Actívalas en los ajustes del sistema", Toast.LENGTH_LONG).show()
                openSystemNotificationSettings()
            }
            else -> enablePush()
        }
    }

    private fun enablePush() {
        NotificationPrefs.pushEnabled = true
        NotificationWorker.schedule(this)
        setSwitches()
    }

    private fun openSystemNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        )
    }

    private fun logout() {
        Session.clear()
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
    }
}
