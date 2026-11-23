package com.baite.adopta

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

/** Cada cuánto se revisan avisos nuevos mientras la app está en primer plano. */
private const val POLL_MS = 30_000L

/** Contenedor principal con la barra de navegación inferior. */
class HomeActivity : AppCompatActivity() {

    private lateinit var nav: BottomNavigationView
    private val askNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* el resultado se lee con areNotificationsEnabled() */ }
    private val poller = Handler(Looper.getMainLooper())
    private val pollTask = object : Runnable {
        override fun run() {
            NotificationRepository.load(applicationContext) { if (!isDestroyed) refreshBadge() }
            poller.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Edge-to-edge (targetSdk 36): respeta barras del sistema.
        val root = findViewById<View>(R.id.homeRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        nav = findViewById(R.id.bottomNav)
        nav.setOnItemSelectedListener { item -> showTab(item.itemId) }
        nav.setOnItemReselectedListener { /* conserva el estado de la pestaña */ }

        refreshBadge()
        if (savedInstanceState == null) askNotificationPermissionOnce()

        // Primero se cargan las mascotas de la API; después se muestra la pestaña inicial.
        PetRepository.load { error ->
            if (isDestroyed) return@load
            if (error != null) {
                Toast.makeText(this, "$error Mostrando datos de ejemplo.", Toast.LENGTH_LONG).show()
            }
            // Solo en el primer arranque; al rotar, el sistema restaura los fragments.
            if (savedInstanceState == null) nav.selectedItemId = R.id.nav_descubrir
        }
    }

    override fun onResume() {
        super.onResume()
        refreshBadge()
        poller.post(pollTask)
    }

    override fun onPause() {
        super.onPause()
        poller.removeCallbacks(pollTask)
    }

    /** Android 13+: pide el permiso de notificaciones una sola vez; después se maneja desde Ajustes. */
    private fun askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (NotificationPrefs.askedPermission || !NotificationPrefs.pushEnabled) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return
        NotificationPrefs.askedPermission = true
        askNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Cambia de pestaña desde un fragment (p. ej. "Explorar mascotas"). */
    fun selectTab(itemId: Int) {
        nav.selectedItemId = itemId
    }

    fun refreshBadge() {
        val unread = NotificationRepository.unread
        if (unread > 0) {
            val badge = nav.getOrCreateBadge(R.id.nav_notificaciones)
            badge.number = unread
            badge.backgroundColor = ContextCompat.getColor(this, R.color.adopta_teal_dark)
            badge.badgeTextColor = ContextCompat.getColor(this, R.color.white)
        } else {
            nav.removeBadge(R.id.nav_notificaciones)
        }
    }

    /** Muestra/oculta fragments en lugar de reemplazarlos: cada pestaña conserva su estado. */
    private fun showTab(itemId: Int): Boolean {
        val factory: () -> Fragment = when (itemId) {
            R.id.nav_descubrir -> { { DiscoverFragment() } }
            R.id.nav_favoritos -> { { FavoritesFragment() } }
            R.id.nav_notificaciones -> { { NotificationsFragment() } }
            R.id.nav_mis_mascotas -> { { MyPetsFragment() } }
            R.id.nav_perfil -> { { ProfileFragment() } }
            else -> return false
        }
        val tag = "tab_$itemId"
        val fm = supportFragmentManager
        val tx = fm.beginTransaction()
            .setReorderingAllowed(true)
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
        fm.fragments.forEach { f ->
            val t = f.tag
            if (t != null && t.startsWith("tab_") && t != tag) tx.hide(f)
        }
        val existing = fm.findFragmentByTag(tag)
        if (existing == null) tx.add(R.id.fragmentContainer, factory(), tag) else tx.show(existing)
        tx.commit()
        return true
    }
}
