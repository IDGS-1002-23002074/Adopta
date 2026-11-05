package com.baite.adopta

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

/** Contenedor principal con la barra de navegación inferior. */
class HomeActivity : AppCompatActivity() {

    private lateinit var nav: BottomNavigationView

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

        // Solo en el primer arranque; al rotar, el sistema restaura los fragments.
        if (savedInstanceState == null) nav.selectedItemId = R.id.nav_descubrir
        refreshBadge()
    }

    override fun onResume() {
        super.onResume()
        refreshBadge()
    }

    /** Cambia de pestaña desde un fragment (p. ej. "Explorar mascotas"). */
    fun selectTab(itemId: Int) {
        nav.selectedItemId = itemId
    }

    private fun refreshBadge() {
        val unread = SampleData.notificaciones.count { !it.leida }
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
