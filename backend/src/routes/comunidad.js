// Comunidad: publicaciones, comentarios y "Me gusta" (RF038-RF042)
const router = require('express').Router();
const { q, one, tx } = require('../db');
const { notify } = require('../services');
const { upload, validar, guardarPublico, borrarPublico } = require('../files');
const { bad, notFound, forbidden, wrap, fileUrl, text, int, pagination } = require('../utils');

const POST_SELECT = `
  SELECT p.id_publicacion,p.contenido,p.imagen,p.fecha_publicacion,p.fecha_actualizacion,p.id_usuario,
         u.nombre,u.apellido_paterno,u.foto_perfil,r.nombre AS rol,
         (SELECT COUNT(*) FROM likes_publicacion l WHERE l.id_publicacion=p.id_publicacion) AS likes,
         (SELECT COUNT(*) FROM comentarios c WHERE c.id_publicacion=p.id_publicacion) AS comentarios,
         EXISTS(SELECT 1 FROM likes_publicacion l WHERE l.id_publicacion=p.id_publicacion AND l.id_usuario=?) AS me_gusta
  FROM publicaciones p JOIN usuarios u ON u.id_usuario=p.id_usuario JOIN roles r ON r.id_rol=u.id_rol`;

const shape = (req, r) => ({
  id_publicacion: r.id_publicacion, contenido: r.contenido, imagen: fileUrl(req, r.imagen),
  fecha_publicacion: r.fecha_publicacion,
  editada: new Date(r.fecha_actualizacion.replace(' ', 'T')) - new Date(r.fecha_publicacion.replace(' ', 'T')) > 2000,
  autor: { id_usuario: r.id_usuario, nombre: `${r.nombre} ${r.apellido_paterno}`, foto: fileUrl(req, r.foto_perfil), rol: r.rol },
  likes: r.likes, comentarios: r.comentarios, me_gusta: !!r.me_gusta, es_mia: r.id_usuario === req.user.id_usuario
});

async function postOr404(id, idUsuario, conn) {
  const r = id && (await one(`${POST_SELECT} WHERE p.id_publicacion=?`, [idUsuario, id], conn));
  if (!r) throw notFound('Publicación no encontrada');
  return r;
}
function contenido(v) {
  const c = text(v, 2000);
  if (!c) throw bad('Escribe algo para publicar', { contenido: 'El contenido es obligatorio' });
  return c;
}

// RF038 — Consultar publicaciones (paginación por id)
router.get('/publicaciones', wrap(async (req, res) => {
  const { limit } = pagination(req, 15, 40);
  const antes = int(req.query.antes);
  const rows = await q(`${POST_SELECT} ${antes ? 'WHERE p.id_publicacion<?' : ''} ORDER BY p.id_publicacion DESC LIMIT ?`,
    antes ? [req.user.id_usuario, antes, limit] : [req.user.id_usuario, limit]);
  res.json({ items: rows.map((r) => shape(req, r)) });
}));
router.get('/publicaciones/:id', wrap(async (req, res) => {
  res.json(shape(req, await postOr404(int(req.params.id), req.user.id_usuario)));
}));

// RF039 — Crear (texto + foto opcional)
router.post('/publicaciones', upload.single('imagen'), wrap(async (req, res) => {
  const c = contenido(req.body?.contenido);
  let ruta = null;
  if (req.file) { const k = validar(req.file, ['jpg', 'png'], 5); ruta = guardarPublico('publicaciones', req.file.buffer, k.ext).ruta; }
  const r = await q('INSERT INTO publicaciones (id_usuario,contenido,imagen) VALUES (?,?,?)', [req.user.id_usuario, c, ruta]);
  res.status(201).json(shape(req, await postOr404(r.insertId, req.user.id_usuario)));
}));

// RF040 — Editar publicación propia (RNF020)
router.put('/publicaciones/:id', upload.single('imagen'), wrap(async (req, res) => {
  const id = int(req.params.id);
  const p = await postOr404(id, req.user.id_usuario);
  if (p.id_usuario !== req.user.id_usuario) throw forbidden('Solo puedes editar tus propias publicaciones');
  const c = contenido(req.body?.contenido);
  let ruta = p.imagen;
  if (req.file) { const k = validar(req.file, ['jpg', 'png'], 5); ruta = guardarPublico('publicaciones', req.file.buffer, k.ext).ruta; }
  else if (req.body?.quitar_imagen === 'true') ruta = null;
  await q('UPDATE publicaciones SET contenido=?,imagen=? WHERE id_publicacion=?', [c, ruta, id]);
  if (p.imagen && p.imagen !== ruta) borrarPublico(p.imagen);
  res.json(shape(req, await postOr404(id, req.user.id_usuario)));
}));

// RF041 — Eliminar publicación propia
router.delete('/publicaciones/:id', wrap(async (req, res) => {
  const id = int(req.params.id);
  const p = await postOr404(id, req.user.id_usuario);
  if (p.id_usuario !== req.user.id_usuario) throw forbidden('Solo puedes eliminar tus propias publicaciones');
  await q('DELETE FROM publicaciones WHERE id_publicacion=?', [id]);
  if (p.imagen) borrarPublico(p.imagen);
  res.json({ eliminada: true });
}));

// RF042 — Me gusta (alterna)
router.post('/publicaciones/:id/like', wrap(async (req, res) => {
  const id = int(req.params.id);
  await postOr404(id, req.user.id_usuario);
  const del = await q('DELETE FROM likes_publicacion WHERE id_publicacion=? AND id_usuario=?', [id, req.user.id_usuario]);
  if (!del.affectedRows) await q('INSERT IGNORE INTO likes_publicacion (id_publicacion,id_usuario) VALUES (?,?)', [id, req.user.id_usuario]);
  const [{ n }] = await q('SELECT COUNT(*) AS n FROM likes_publicacion WHERE id_publicacion=?', [id]);
  res.json({ me_gusta: !del.affectedRows, likes: n });
}));

// RF042 — Comentarios
const shapeC = (req, r) => ({
  id_comentario: r.id_comentario, id_publicacion: r.id_publicacion, contenido: r.contenido, fecha_comentario: r.fecha_comentario,
  autor: { id_usuario: r.id_usuario, nombre: `${r.nombre} ${r.apellido_paterno}`, foto: fileUrl(req, r.foto_perfil) },
  es_mio: r.id_usuario === req.user.id_usuario
});
const COM_SELECT = `SELECT c.*,u.nombre,u.apellido_paterno,u.foto_perfil FROM comentarios c JOIN usuarios u ON u.id_usuario=c.id_usuario`;

router.get('/publicaciones/:id/comentarios', wrap(async (req, res) => {
  const id = int(req.params.id);
  await postOr404(id, req.user.id_usuario);
  const rows = await q(`${COM_SELECT} WHERE c.id_publicacion=? ORDER BY c.fecha_comentario,c.id_comentario`, [id]);
  res.json({ items: rows.map((r) => shapeC(req, r)) });
}));
router.post('/publicaciones/:id/comentarios', wrap(async (req, res) => {
  const id = int(req.params.id);
  const c = text(req.body?.contenido, 1000);
  if (!c) throw bad('Escribe un comentario', { contenido: 'El comentario es obligatorio' });
  const nuevo = await tx(async (conn) => {
    const p = await postOr404(id, req.user.id_usuario, conn);
    const r = await q('INSERT INTO comentarios (id_publicacion,id_usuario,contenido) VALUES (?,?,?)', [id, req.user.id_usuario, c], conn);
    if (p.id_usuario !== req.user.id_usuario) {
      await notify(conn, p.id_usuario, 'Nuevo comentario', `${req.user.nombre} comentó tu publicación: "${c.slice(0, 80)}"`, 'Comunidad', null);
    }
    return r.insertId;
  });
  res.status(201).json(shapeC(req, await one(`${COM_SELECT} WHERE c.id_comentario=?`, [nuevo])));
}));
router.put('/comentarios/:id', wrap(async (req, res) => {
  const id = int(req.params.id);
  const c = text(req.body?.contenido, 1000);
  if (!c) throw bad('Escribe un comentario', { contenido: 'El comentario es obligatorio' });
  const cm = id && (await one('SELECT id_usuario FROM comentarios WHERE id_comentario=?', [id]));
  if (!cm) throw notFound('Comentario no encontrado');
  if (cm.id_usuario !== req.user.id_usuario) throw forbidden('Solo puedes editar tus comentarios');
  await q('UPDATE comentarios SET contenido=? WHERE id_comentario=?', [c, id]);
  res.json(shapeC(req, await one(`${COM_SELECT} WHERE c.id_comentario=?`, [id])));
}));
router.delete('/comentarios/:id', wrap(async (req, res) => {
  const id = int(req.params.id);
  const cm = id && (await one('SELECT id_usuario FROM comentarios WHERE id_comentario=?', [id]));
  if (!cm) throw notFound('Comentario no encontrado');
  if (cm.id_usuario !== req.user.id_usuario) throw forbidden('Solo puedes eliminar tus comentarios');
  await q('DELETE FROM comentarios WHERE id_comentario=?', [id]);
  res.json({ eliminado: true });
}));

module.exports = router;
