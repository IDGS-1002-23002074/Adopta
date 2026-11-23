// Mi hogar: adopciones, seguimiento (RF028, RF031, RF032) y devoluciones (RF033, RF037)
const router = require('express').Router();
const { q, one, tx } = require('../db');
const cat = require('../catalogos');
const { notify, notifyOrgDeMascota } = require('../services');
const { upload, validar, guardarPublico } = require('../files');
const { bad, notFound, conflict, wrap, fileUrl, text, int } = require('../utils');

const ADOPCION_SELECT = `
  SELECT a.id_adopcion,a.fecha_adopcion,a.observaciones,s.id_solicitud,
         m.id_mascota,m.nombre,m.especie,m.raza,m.sexo,m.edad_aproximada,m.tamano,
         em.nombre AS estado_mascota,ea.nombre AS estado_adaptacion,o.nombre AS organizacion,
         (SELECT fm.ruta_foto FROM fotos_mascota fm WHERE fm.id_mascota=m.id_mascota ORDER BY fm.es_principal DESC,fm.id_foto LIMIT 1) AS foto,
         (SELECT COUNT(*) FROM reportes_seguimiento r WHERE r.id_adopcion=a.id_adopcion) AS total_reportes,
         (SELECT MAX(r.fecha_reporte) FROM reportes_seguimiento r WHERE r.id_adopcion=a.id_adopcion) AS ultimo_reporte,
         (SELECT d.estado FROM solicitudes_devolucion d WHERE d.id_adopcion=a.id_adopcion) AS estado_devolucion
  FROM adopciones a
  JOIN solicitudes_adopcion s ON s.id_solicitud=a.id_solicitud
  JOIN mascotas m ON m.id_mascota=s.id_mascota
  JOIN estados_mascota em ON em.id_estado_mascota=m.id_estado_mascota
  JOIN estados_adaptacion ea ON ea.id_estado_adaptacion=m.id_estado_adaptacion
  JOIN organizaciones o ON o.id_organizacion=m.id_organizacion`;

function shapeAdopcion(req, r) {
  return {
    id_adopcion: r.id_adopcion, fecha_adopcion: r.fecha_adopcion, id_solicitud: r.id_solicitud,
    id_mascota: r.id_mascota, nombre: r.nombre, especie: r.especie, raza: r.raza, sexo: r.sexo,
    edad_aproximada: r.edad_aproximada, tamano: r.tamano, organizacion: r.organizacion, foto: fileUrl(req, r.foto),
    estado_mascota: r.estado_mascota, estado_adaptacion: r.estado_adaptacion,
    total_reportes: r.total_reportes, ultimo_reporte: r.ultimo_reporte, estado_devolucion: r.estado_devolucion || null
  };
}
async function mia(id, idUsuario, conn) {
  const r = await one(`${ADOPCION_SELECT} WHERE a.id_adopcion=? AND s.id_adoptante=?`, [id, idUsuario], conn);
  if (!r) throw notFound('Adopción no encontrada');
  return r;
}

// RF031 — Mis mascotas adoptadas y su seguimiento
router.get('/adopciones', wrap(async (req, res) => {
  const rows = await q(`${ADOPCION_SELECT} WHERE s.id_adoptante=? ORDER BY a.fecha_adopcion DESC,a.id_adopcion DESC`, [req.user.id_usuario]);
  res.json({ items: rows.map((r) => shapeAdopcion(req, r)) });
}));

// RF031/RF032 — Detalle con historial de reportes y de cambios de adaptación
router.get('/adopciones/:id', wrap(async (req, res) => {
  const id = int(req.params.id);
  const r = await mia(id, req.user.id_usuario);
  const reportes = await q(
    `SELECT id_reporte,comportamiento,salud,alimentacion,adaptacion,observaciones,fecha_reporte
     FROM reportes_seguimiento WHERE id_adopcion=? ORDER BY fecha_reporte DESC,id_reporte DESC`, [id]);
  const fotos = reportes.length ? await q('SELECT id_reporte,ruta_foto FROM fotos_seguimiento WHERE id_reporte IN (?)', [reportes.map((x) => x.id_reporte)]) : [];
  const hist = await q(
    `SELECT h.id_historial_adaptacion,ea.nombre AS estado,h.observaciones,h.fecha_cambio,CONCAT(u.nombre,' ',u.apellido_paterno) AS responsable,ro.nombre AS rol
     FROM historial_adaptacion h
     JOIN estados_adaptacion ea ON ea.id_estado_adaptacion=h.id_estado_adaptacion
     JOIN usuarios u ON u.id_usuario=h.id_usuario_responsable JOIN roles ro ON ro.id_rol=u.id_rol
     WHERE h.id_mascota=? ORDER BY h.fecha_cambio DESC,h.id_historial_adaptacion DESC`, [r.id_mascota]);
  const dev = await one(
    `SELECT d.id_devolucion,d.estado,d.explicacion,d.fecha_solicitud,d.fecha_revision,d.observaciones_organizacion
     FROM solicitudes_devolucion d WHERE d.id_adopcion=?`, [id]);
  res.json({
    ...shapeAdopcion(req, r), observaciones: r.observaciones,
    reportes: reportes.map((x) => ({ ...x, fotos: fotos.filter((f) => f.id_reporte === x.id_reporte).map((f) => fileUrl(req, f.ruta_foto)) })),
    historial_adaptacion: hist,
    devolucion: dev,
    puede_reportar: !dev,
    puede_devolver: !dev
  });
}));

// RF028 — Registrar reporte de adaptación (texto + fotos)
router.post('/adopciones/:id/reportes', upload.array('fotos', 5), wrap(async (req, res) => {
  const id = int(req.params.id);
  const b = req.body || {};
  const campos = {
    comportamiento: text(b.comportamiento, 2000), salud: text(b.salud, 2000), alimentacion: text(b.alimentacion, 2000),
    adaptacion: text(b.adaptacion, 2000), observaciones: text(b.observaciones, 2000)
  };
  if (!Object.values(campos).some(Boolean)) throw bad('Cuéntanos al menos un dato sobre tu mascota', { comportamiento: 'Completa al menos un campo' });
  const fotos = (req.files || []).map((f) => ({ f, kind: validar(f, ['jpg', 'png'], 5) }));

  const idReporte = await tx(async (conn) => {
    const a = await mia(id, req.user.id_usuario, conn);
    if (a.estado_devolucion) throw conflict('Esta adopción tiene una solicitud de devolución en curso');
    const r = await q(
      'INSERT INTO reportes_seguimiento (id_adopcion,comportamiento,salud,alimentacion,adaptacion,observaciones) VALUES (?,?,?,?,?,?)',
      [id, campos.comportamiento, campos.salud, campos.alimentacion, campos.adaptacion, campos.observaciones], conn);
    for (const { f, kind } of fotos) {
      const { ruta } = guardarPublico('seguimiento', f.buffer, kind.ext);
      await q('INSERT INTO fotos_seguimiento (id_reporte,ruta_foto) VALUES (?,?)', [r.insertId, ruta], conn);
    }
    await notify(conn, req.user.id_usuario, 'Reporte enviado', `Registraste el seguimiento de ${a.nombre}. ¡Gracias por mantenernos al tanto!`, 'Seguimiento', null);
    await notifyOrgDeMascota(conn, a.id_mascota, 'Nuevo reporte de seguimiento',
      `${req.user.nombre} registró un reporte sobre ${a.nombre}.`, 'Seguimiento', null);
    return r.insertId;
  });
  res.status(201).json({ id_reporte: idReporte });
}));

// ---------------------------------------------------------------- devoluciones
const DEV_SELECT = `
  SELECT d.id_devolucion,d.id_adopcion,d.estado,d.explicacion,d.fecha_solicitud,d.fecha_revision,d.observaciones_organizacion,
         m.id_mascota,m.nombre AS mascota,m.especie,o.nombre AS organizacion,
         (SELECT fm.ruta_foto FROM fotos_mascota fm WHERE fm.id_mascota=m.id_mascota ORDER BY fm.es_principal DESC,fm.id_foto LIMIT 1) AS foto,
         (SELECT GROUP_CONCAT(mo.nombre ORDER BY mo.id_motivo SEPARATOR '|') FROM detalle_motivos_devolucion dm
            JOIN motivos_devolucion mo ON mo.id_motivo=dm.id_motivo WHERE dm.id_devolucion=d.id_devolucion) AS motivos
  FROM solicitudes_devolucion d
  JOIN adopciones a ON a.id_adopcion=d.id_adopcion
  JOIN solicitudes_adopcion s ON s.id_solicitud=a.id_solicitud
  JOIN mascotas m ON m.id_mascota=s.id_mascota
  JOIN organizaciones o ON o.id_organizacion=m.id_organizacion`;
const shapeDev = (req, r) => ({
  id_devolucion: r.id_devolucion, id_adopcion: r.id_adopcion, estado: r.estado, explicacion: r.explicacion,
  fecha_solicitud: r.fecha_solicitud, fecha_revision: r.fecha_revision, observaciones_organizacion: r.observaciones_organizacion,
  id_mascota: r.id_mascota, mascota: r.mascota, especie: r.especie, organizacion: r.organizacion, foto: fileUrl(req, r.foto),
  motivos: r.motivos ? r.motivos.split('|') : []
});

// RF037 — Mis solicitudes de devolución
router.get('/devoluciones', wrap(async (req, res) => {
  const rows = await q(`${DEV_SELECT} WHERE s.id_adoptante=? ORDER BY d.fecha_solicitud DESC`, [req.user.id_usuario]);
  res.json({ items: rows.map((r) => shapeDev(req, r)) });
}));

// RF033 — Registrar solicitud de devolución
router.post('/devoluciones', wrap(async (req, res) => {
  const b = req.body || {};
  const idAdopcion = int(b.id_adopcion);
  const motivos = [...new Set((Array.isArray(b.motivos) ? b.motivos : []).map(int).filter(Boolean))];
  const explicacion = text(b.explicacion, 3000);
  const e = {};
  if (!idAdopcion) e.id_adopcion = 'Selecciona la mascota';
  if (!motivos.length) e.motivos = 'Selecciona al menos un motivo';
  if (!explicacion || explicacion.length < 10) e.explicacion = 'Explica tu situación (mínimo 10 caracteres)';
  if (Object.keys(e).length) throw bad('Revisa los datos de la solicitud', e);

  const id = await tx(async (conn) => {
    const a = await mia(idAdopcion, req.user.id_usuario, conn);
    if (a.estado_devolucion) throw conflict('Ya enviaste una solicitud de devolución para esta mascota');
    const validos = await q('SELECT id_motivo FROM motivos_devolucion WHERE id_motivo IN (?)', [motivos], conn);
    if (validos.length !== motivos.length) throw bad('Motivo no válido', { motivos: 'Motivo no válido' });
    const r = await q('INSERT INTO solicitudes_devolucion (id_adopcion,explicacion) VALUES (?,?)', [idAdopcion, explicacion], conn);
    for (const m of motivos) await q('INSERT INTO detalle_motivos_devolucion (id_devolucion,id_motivo) VALUES (?,?)', [r.insertId, m], conn);
    // RNF015: la mascota pasa a "En proceso de devolucion"
    await q('UPDATE mascotas SET id_estado_mascota=? WHERE id_mascota=?', [await cat.id('estadoMascota', 'En proceso de devolucion'), a.id_mascota], conn);
    await notify(conn, req.user.id_usuario, 'Solicitud de devolución enviada', `${a.organizacion} revisará tu solicitud sobre ${a.nombre}.`, 'Devoluciones', null);
    await notifyOrgDeMascota(conn, a.id_mascota, 'Solicitud de devolución',
      `${req.user.nombre} solicitó devolver a ${a.nombre}.`, 'Devoluciones', null);
    return r.insertId;
  });
  const r = await one(`${DEV_SELECT} WHERE d.id_devolucion=?`, [id]);
  res.status(201).json(shapeDev(req, r));
}));

module.exports = router;
