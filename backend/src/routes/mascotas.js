// Catálogo de mascotas (RF015-RF017) y favoritos (RF019)
const router = require('express').Router();
const { q, one } = require('../db');
const { wrap, notFound, fileUrl, pagination, int, text } = require('../utils');

const EDAD_MESES = `COALESCE(TIMESTAMPDIFF(MONTH, m.fecha_nacimiento, CURDATE()),
  CASE WHEN m.edad_aproximada LIKE '%mes%' THEN CAST(SUBSTRING_INDEX(TRIM(m.edad_aproximada),' ',1) AS UNSIGNED)
       WHEN m.edad_aproximada REGEXP '^[0-9]+' THEN CAST(SUBSTRING_INDEX(TRIM(m.edad_aproximada),' ',1) AS UNSIGNED)*12 END)`;

const SELECT_BASE = `
  SELECT m.id_mascota,m.nombre,m.especie,m.raza,m.sexo,m.edad_aproximada,m.tamano,m.color,m.vacunado,m.esterilizado,
         em.nombre AS estado_mascota,o.id_organizacion,o.nombre AS organizacion,o.ciudad,uo.foto_perfil AS organizacion_logo,
         (SELECT fm.ruta_foto FROM fotos_mascota fm WHERE fm.id_mascota=m.id_mascota
            ORDER BY fm.es_principal DESC, fm.id_foto LIMIT 1) AS foto_principal,
         EXISTS(SELECT 1 FROM favoritos f WHERE f.id_mascota=m.id_mascota AND f.id_usuario=?) AS es_favorito
  FROM mascotas m
  JOIN estados_mascota em ON em.id_estado_mascota=m.id_estado_mascota
  JOIN organizaciones o ON o.id_organizacion=m.id_organizacion
  JOIN usuarios uo ON uo.id_usuario=o.id_usuario`;

function shape(req, r) {
  return {
    id_mascota: r.id_mascota, nombre: r.nombre, especie: r.especie, raza: r.raza, sexo: r.sexo,
    edad_aproximada: r.edad_aproximada, tamano: r.tamano, color: r.color,
    vacunado: !!r.vacunado, esterilizado: !!r.esterilizado, estado_mascota: r.estado_mascota,
    id_organizacion: r.id_organizacion, organizacion: r.organizacion, ciudad: r.ciudad,
    organizacion_logo: fileUrl(req, r.organizacion_logo),
    foto_principal: fileUrl(req, r.foto_principal), es_favorito: !!r.es_favorito
  };
}

// Valores para los filtros y formularios
router.get('/catalogos', wrap(async (req, res) => {
  const [especies, razas, ciudades, motivos, tipos] = await Promise.all([
    q(`SELECT DISTINCT especie FROM mascotas ORDER BY especie`),
    q(`SELECT DISTINCT raza FROM mascotas WHERE raza IS NOT NULL AND raza<>'' ORDER BY raza`),
    q(`SELECT DISTINCT o.ciudad FROM organizaciones o JOIN mascotas m ON m.id_organizacion=o.id_organizacion
       WHERE o.ciudad IS NOT NULL AND o.ciudad<>'' ORDER BY o.ciudad`),
    q('SELECT id_motivo,nombre FROM motivos_devolucion ORDER BY id_motivo'),
    q('SELECT clave,nombre,descripcion,formatos_permitidos,tamano_max_mb,min_archivos,max_archivos,requiere_fecha_emision,vigencia_meses FROM tipos_documento WHERE activo=TRUE')
  ]);
  res.json({
    especies: especies.map((r) => r.especie), razas: razas.map((r) => r.raza), ciudades: ciudades.map((r) => r.ciudad),
    tamanos: ['Pequeno', 'Mediano', 'Grande'], sexos: ['Macho', 'Hembra'],
    motivos_devolucion: motivos, tipos_documento: tipos
  });
}));

// RF015/RF016 — Consulta, búsqueda y filtrado
router.get('/mascotas', wrap(async (req, res) => {
  const { limit, offset } = pagination(req, 20, 50);
  const where = [`em.nombre='Disponible'`, `o.estado='Activa'`];
  const params = [req.user.id_usuario];
  const f = req.query;
  const txt = text(f.q, 80);
  if (txt) {
    where.push('(m.nombre LIKE ? OR m.raza LIKE ? OR m.especie LIKE ? OR o.nombre LIKE ? OR o.ciudad LIKE ?)');
    const like = `%${txt.replace(/[%_]/g, '')}%`;
    params.push(like, like, like, like, like);
  }
  if (text(f.especie, 50)) { where.push('m.especie=?'); params.push(text(f.especie, 50)); }
  if (text(f.raza, 80)) { where.push('m.raza=?'); params.push(text(f.raza, 80)); }
  if (['Pequeno', 'Mediano', 'Grande'].includes(f.tamano)) { where.push('m.tamano=?'); params.push(f.tamano); }
  if (['Macho', 'Hembra'].includes(f.sexo)) { where.push('m.sexo=?'); params.push(f.sexo); }
  if (text(f.ciudad, 80)) { where.push('o.ciudad=?'); params.push(text(f.ciudad, 80)); }
  const edad = int(f.edad);
  if (edad === 0) where.push(`${EDAD_MESES} < 12`);
  else if (edad === 1) where.push(`${EDAD_MESES} BETWEEN 12 AND 47`);
  else if (edad === 2) where.push(`${EDAD_MESES} >= 48`);

  const W = where.join(' AND ');
  const [{ total }] = await q(
    `SELECT COUNT(*) AS total FROM mascotas m
     JOIN estados_mascota em ON em.id_estado_mascota=m.id_estado_mascota
     JOIN organizaciones o ON o.id_organizacion=m.id_organizacion WHERE ${W}`, params.slice(1));
  const rows = await q(`${SELECT_BASE} WHERE ${W} ORDER BY m.fecha_registro DESC, m.id_mascota DESC LIMIT ? OFFSET ?`,
    [...params, limit, offset]);
  res.json({ total, items: rows.map((r) => shape(req, r)) });
}));

// RF017 — Detalle
router.get('/mascotas/:id', wrap(async (req, res) => {
  const id = int(req.params.id);
  const r = id && (await one(`${SELECT_BASE} WHERE m.id_mascota=?`, [req.user.id_usuario, id]));
  if (!r) throw notFound('Mascota no encontrada');
  const m = await one(
    `SELECT descripcion,comportamiento,estado_salud,peso,fecha_nacimiento FROM mascotas WHERE id_mascota=?`, [id]);
  const org = await one(
    `SELECT descripcion,direccion,telefono,correo_contacto,sitio_web FROM organizaciones WHERE id_organizacion=?`, [r.id_organizacion]);
  const fotos = await q('SELECT ruta_foto FROM fotos_mascota WHERE id_mascota=? ORDER BY es_principal DESC,id_foto', [id]);
  const sol = await one(
    `SELECT s.id_solicitud,es.nombre AS estado FROM solicitudes_adopcion s
     JOIN estados_solicitud es ON es.id_estado_solicitud=s.id_estado_solicitud
     WHERE s.id_mascota=? AND s.id_adoptante=? ORDER BY s.id_solicitud DESC LIMIT 1`, [id, req.user.id_usuario]);
  res.json({
    ...shape(req, r),
    descripcion: m.descripcion, comportamiento: m.comportamiento, estado_salud: m.estado_salud,
    peso: m.peso, fecha_nacimiento: m.fecha_nacimiento,
    fotos: fotos.map((f) => fileUrl(req, f.ruta_foto)),
    organizacion_detalle: {
      id_organizacion: r.id_organizacion, nombre: r.organizacion, descripcion: org.descripcion, direccion: org.direccion,
      ciudad: r.ciudad, telefono: org.telefono, correo_contacto: org.correo_contacto, sitio_web: org.sitio_web,
      logo: fileUrl(req, r.organizacion_logo)
    },
    mi_solicitud: sol ? { id_solicitud: sol.id_solicitud, estado: sol.estado } : null
  });
}));

// RF019 — Favoritos
router.get('/favoritos', wrap(async (req, res) => {
  const rows = await q(
    `${SELECT_BASE} JOIN favoritos fv ON fv.id_mascota=m.id_mascota AND fv.id_usuario=?
     ORDER BY fv.fecha_agregado DESC`, [req.user.id_usuario, req.user.id_usuario]);
  res.json({ total: rows.length, items: rows.map((r) => shape(req, r)) });
}));
router.post('/favoritos/:idMascota', wrap(async (req, res) => {
  const id = int(req.params.idMascota);
  if (!id || !(await one('SELECT 1 AS x FROM mascotas WHERE id_mascota=?', [id]))) throw notFound('Mascota no encontrada');
  await q('INSERT IGNORE INTO favoritos (id_usuario,id_mascota) VALUES (?,?)', [req.user.id_usuario, id]);
  res.status(201).json({ es_favorito: true });
}));
router.delete('/favoritos/:idMascota', wrap(async (req, res) => {
  await q('DELETE FROM favoritos WHERE id_usuario=? AND id_mascota=?', [req.user.id_usuario, int(req.params.idMascota)]);
  res.json({ es_favorito: false });
}));

module.exports = router;
