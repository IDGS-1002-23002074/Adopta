package com.baite.adopta

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.baite.adopta.data.ApiClient
import com.baite.adopta.data.Comentario
import com.baite.adopta.data.ContenidoReq
import com.baite.adopta.data.Format
import com.baite.adopta.data.Outcome
import com.baite.adopta.data.Publicacion
import com.baite.adopta.data.api
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/** RF042 — Publicación con sus comentarios (agregar, editar y eliminar los propios). */
class PostDetailActivity : BaseActivity() {

    private var postId = -1
    private var post: Publicacion? = null
    private var comments = mutableListOf<Comentario>()
    private var changed = false

    private val editor = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) { changed = true; load() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post_detail)
        postId = intent.getIntExtra(Extras.POST_ID, -1)
        if (postId < 0) { finish(); return }
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.btnEnviarComentario).setOnClickListener { sendComment() }
        load()
    }

    override fun finish() {
        if (changed) setResult(Activity.RESULT_OK)
        super.finish()
    }

    private fun load() {
        val bar = findViewById<View>(R.id.progressTop)
        bar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val p = api { ApiClient.api.publicacion(postId) }
            val c = api { ApiClient.api.comentarios(postId) }
            when {
                p is Outcome.Fail -> {
                    if (p.status == 404) { changed = true; finish() } else findViewById<View>(android.R.id.content).showFail(p) { load() }
                }
                c is Outcome.Fail -> findViewById<View>(android.R.id.content).showFail(c) { load() }
                p is Outcome.Ok && c is Outcome.Ok -> {
                    post = p.value
                    comments = c.value.items.toMutableList()
                    renderPost()
                    renderComments()
                }
            }
            bar.visibility = View.INVISIBLE
        }
    }

    private fun renderPost() {
        val p = post ?: return
        val box = findViewById<FrameLayout>(R.id.postContainer)
        box.removeAllViews()
        val v = LayoutInflater.from(this).inflate(R.layout.item_post, box, false)
        PostBinder.bind(v, p, onLike = ::toggleLike, onOpen = null, onMenu = ::postMenu)
        v.findViewById<View>(R.id.btnComentar).setOnClickListener { findViewById<EditText>(R.id.etComentario).requestFocus() }
        box.addView(v)
    }

    private fun toggleLike(p: Publicacion) {
        post = p.copy(meGusta = !p.meGusta, likes = p.likes + if (p.meGusta) -1 else 1)
        renderPost()
        changed = true
        lifecycleScope.launch {
            when (val r = api { ApiClient.api.like(p.idPublicacion) }) {
                is Outcome.Ok -> { post = p.copy(meGusta = r.value.meGusta, likes = r.value.likes); renderPost() }
                is Outcome.Fail -> { post = p; renderPost(); findViewById<View>(android.R.id.content).showFail(r) }
            }
        }
    }

    private fun postMenu(p: Publicacion, anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "Editar")
            menu.add(0, 2, 1, "Eliminar")
            setOnMenuItemClickListener {
                if (it.itemId == 1) editor.launch(Intent(this@PostDetailActivity, PostEditorActivity::class.java).putExtra(Extras.POST_ID, p.idPublicacion))
                else MaterialAlertDialogBuilder(this@PostDetailActivity)
                    .setTitle("¿Eliminar publicación?")
                    .setPositiveButton("Eliminar") { _, _ ->
                        lifecycleScope.launch {
                            when (val r = api { ApiClient.api.eliminarPublicacion(p.idPublicacion) }) {
                                is Outcome.Ok -> { changed = true; finish() }
                                is Outcome.Fail -> findViewById<View>(android.R.id.content).showFail(r)
                            }
                        }
                    }
                    .setNegativeButton("Cancelar", null).show()
                true
            }
        }.show()
    }

    private fun renderComments() {
        val ll = findViewById<LinearLayout>(R.id.llComentarios)
        ll.removeAllViews()
        findViewById<View>(R.id.tvSinComentarios).visible(comments.isEmpty())
        comments.forEach { c ->
            val v = LayoutInflater.from(this).inflate(R.layout.item_comment, ll, false)
            v.findViewById<ImageView>(R.id.ivAvatar).loadAvatar(c.autor.foto)
            v.findViewById<TextView>(R.id.tvAutor).text = c.autor.nombre
            v.findViewById<TextView>(R.id.tvFecha).text = Format.relative(c.fechaComentario)
            v.findViewById<TextView>(R.id.tvContenido).text = c.contenido
            v.findViewById<View>(R.id.btnMas).apply {
                visible(c.esMio)
                setOnClickListener { commentMenu(c, this) }
            }
            ll.addView(v)
        }
    }

    private fun commentMenu(c: Comentario, anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "Editar")
            menu.add(0, 2, 1, "Eliminar")
            setOnMenuItemClickListener {
                if (it.itemId == 1) editComment(c) else deleteComment(c)
                true
            }
        }.show()
    }

    private fun editComment(c: Comentario) {
        val input = EditText(this).apply { setText(c.contenido); setPadding(dp(24), dp(16), dp(24), dp(8)) }
        MaterialAlertDialogBuilder(this)
            .setTitle("Editar comentario")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val texto = input.text.toString().trim()
                if (texto.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    when (val r = api { ApiClient.api.editarComentario(c.idComentario, ContenidoReq(texto)) }) {
                        is Outcome.Ok -> {
                            val i = comments.indexOfFirst { it.idComentario == c.idComentario }
                            if (i >= 0) comments[i] = r.value
                            renderComments()
                        }
                        is Outcome.Fail -> findViewById<View>(android.R.id.content).showFail(r)
                    }
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun deleteComment(c: Comentario) {
        MaterialAlertDialogBuilder(this)
            .setTitle("¿Eliminar comentario?")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    when (val r = api { ApiClient.api.eliminarComentario(c.idComentario) }) {
                        is Outcome.Ok -> {
                            comments.removeAll { it.idComentario == c.idComentario }
                            post = post?.copy(comentarios = maxOf(0, (post?.comentarios ?: 1) - 1))
                            changed = true
                            renderPost(); renderComments()
                        }
                        is Outcome.Fail -> findViewById<View>(android.R.id.content).showFail(r)
                    }
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun sendComment() {
        val et = findViewById<EditText>(R.id.etComentario)
        val texto = et.text.toString().trim()
        if (texto.isEmpty()) return
        val btn = findViewById<View>(R.id.btnEnviarComentario)
        btn.isEnabled = false
        lifecycleScope.launch {
            when (val r = api { ApiClient.api.comentar(postId, ContenidoReq(texto)) }) {
                is Outcome.Ok -> {
                    comments.add(r.value)
                    post = post?.copy(comentarios = (post?.comentarios ?: 0) + 1)
                    changed = true
                    et.text.clear()
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(et.windowToken, 0)
                    renderPost(); renderComments()
                    findViewById<androidx.core.widget.NestedScrollView>(R.id.scroll).post { findViewById<androidx.core.widget.NestedScrollView>(R.id.scroll).fullScroll(View.FOCUS_DOWN) }
                }
                is Outcome.Fail -> findViewById<View>(android.R.id.content).showFail(r) { sendComment() }
            }
            btn.isEnabled = true
        }
    }
}
