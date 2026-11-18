// RF043 — Notificaciones del sistema
const router = require('express').Router();
const { q } = require('../db');
const { wrap, int, text, pagination } = require('../utils');

router.get('/', wrap(async (req, res) => {
  const { limit, offset } = pagination(req, 30, 100);
  const tipo = text(req.query.tipo, 50);
  const where = ['n.id_usuario=?']; const params = [req.user.id_usuario];
  if (tipo) { where.push('n.tipo=?'); params.push(tipo); }
  const rows = await q(
    `SELECT n.id_notificacion,n.titulo,n.mensaje,n.tipo,n.leida,n.fecha_envio,n.id_solicitud,es.nombre AS estado_solicitud
     FROM notificaciones n
     LEFT JOIN solicitudes_adopcion s ON s.id_solicitud=n.id_solicitud
     LEFT JOIN estados_solicitud es ON es.id_estado_solicitud=s.id_estado_solicitud
     WHERE ${where.join(' AND ')} ORDER BY n.fecha_envio DESC,n.id_notificacion DESC LIMIT ? OFFSET ?`, [...params, limit, offset]);
  const [{ no_leidas }] = await q('SELECT COUNT(*) AS no_leidas FROM notificaciones WHERE id_usuario=? AND leida=FALSE', [req.user.id_usuario]);
  res.json({ items: rows.map((r) => ({ ...r, leida: !!r.leida })), no_leidas });
}));

router.get('/no-leidas', wrap(async (req, res) => {
  const [{ total }] = await q('SELECT COUNT(*) AS total FROM notificaciones WHERE id_usuario=? AND leida=FALSE', [req.user.id_usuario]);
  res.json({ total });
}));

router.post('/leer-todas', wrap(async (req, res) => {
  await q('UPDATE notificaciones SET leida=TRUE WHERE id_usuario=? AND leida=FALSE', [req.user.id_usuario]);
  res.json({ total: 0 });
}));

router.post('/:id/leida', wrap(async (req, res) => {
  await q('UPDATE notificaciones SET leida=TRUE WHERE id_notificacion=? AND id_usuario=?', [int(req.params.id), req.user.id_usuario]);
  const [{ total }] = await q('SELECT COUNT(*) AS total FROM notificaciones WHERE id_usuario=? AND leida=FALSE', [req.user.id_usuario]);
  res.json({ total });
}));

module.exports = router;
