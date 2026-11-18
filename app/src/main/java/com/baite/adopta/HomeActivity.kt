package com.baite.adopta

import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.api
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

/** Contenedor principal con la barra de navegación inferior. */
class HomeActivity : BaseActivity() {

    private lateinit var nav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        nav = findViewById(R.id.bottomNav)
        nav.setOnItemSelectedListener { item -> showTab(item.itemId) }
        nav.setOnItemReselectedListener { /* conserva el estado de la pestaña */ }

        // Solo en el primer arranque; al rotar, el sistema restaura los fragments.
        if (savedInstanceState == null) nav.selectedItemId = R.id.nav_mascotas
    }

    override fun onResume() {
        super.onResume()
        refreshBadge()
    }

    /** Cambia de pestaña desde un fragment (p. ej. "Explorar mascotas"). */
    fun selectTab(itemId: Int) {
        nav.selectedItemId = itemId
    }

    /** Abre "Mi hogar" directamente en una de sus secciones. */
    fun openHome(section: Int) {
        MyHomeFragment.requestedSection = section
        val f = supportFragmentManager.findFragmentByTag("tab_${R.id.nav_mis_mascotas}") as? MyHomeFragment
        nav.selectedItemId = R.id.nav_mis_mascotas
        f?.showRequestedSection()
    }

    /** RF043 — número de notificaciones sin leer sobre el ícono de Actividad. */
    fun refreshBadge() {
        lifecycleScope.launch {
            val r = api { ApiClient.api.noLeidas() } as? Outcome.Ok ?: return@launch
            val unread = r.value.total
            if (unread > 0) {
                val badge = nav.getOrCreateBadge(R.id.nav_notificaciones)
                badge.number = unread
                badge.backgroundColor = ContextCompat.getColor(this@HomeActivity, R.color.adopta_teal_dark)
                badge.badgeTextColor = ContextCompat.getColor(this@HomeActivity, R.color.white)
            } else {
                nav.removeBadge(R.id.nav_notificaciones)
            }
        }
    }

    /** Muestra/oculta fragments en lugar de reemplazarlos: cada pestaña conserva su estado. */
    private fun showTab(itemId: Int): Boolean {
        val factory: () -> Fragment = when (itemId) {
            R.id.nav_mascotas -> { { CatalogFragment() } }
            R.id.nav_comunidad -> { { CommunityFragment() } }
            R.id.nav_mis_mascotas -> { { MyHomeFragment() } }
            R.id.nav_notificaciones -> { { NotificationsFragment() } }
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
