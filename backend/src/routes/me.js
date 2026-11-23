const router = require('express').Router();
const bcrypt = require('bcryptjs');
const { q, one, tx } = require('../db');
const { upload, validar, guardarPublico, borrarPublico } = require('../files');
const { HttpError, bad, wrap, fileUrl, text, bool, phoneOk, passwordError } = require('../utils');

async function perfilCompleto(req, id) {
  const u = await one(
    `SELECT u.id_usuario,u.nombre,u.apellido_paterno,u.apellido_materno,u.correo,u.telefono,u.foto_perfil,u.fecha_registro,r.nombre AS rol
     FROM usuarios u JOIN roles r ON r.id_rol=u.id_rol WHERE u.id_usuario=?`, [id]);
  const perfil = (await one('SELECT * FROM perfiles_adoptantes WHERE id_usuario=?', [id])) || {};
  delete perfil.id_usuario;
  const pref = (await one('SELECT especie,tamano,edad_preferida FROM preferencias_usuario WHERE id_usuario=? ORDER BY id_preferencia DESC LIMIT 1', [id]))
    || { especie: null, tamano: null, edad_preferida: null };
  const res = await one(
    `SELECT
      (SELECT COUNT(*) FROM favoritos WHERE id_usuario=?) AS favoritos,
      (SELECT COUNT(*) FROM adopciones a JOIN solicitudes_adopcion s ON s.id_solicitud=a.id_solicitud WHERE s.id_adoptante=?) AS adopciones,
      (SELECT COUNT(*) FROM solicitudes_adopcion s JOIN estados_solicitud e ON e.id_estado_solicitud=s.id_estado_solicitud
        WHERE s.id_adoptante=? AND e.nombre IN ('Pendiente','En revision','Documentacion pendiente')) AS solicitudes_activas`,
    [id, id, id]);
  const foto = u.foto_perfil;
  u.foto_perfil = fileUrl(req, foto);
  return { usuario: u, perfil, preferencias: pref, resumen: res };
}

// RF004 — Consultar perfil
router.get('/', wrap(async (req, res) => res.json(await perfilCompleto(req, req.user.id_usuario))));

// RF004 — Actualizar datos personales y de contacto
router.put('/', wrap(async (req, res) => {
  const b = req.body || {};
  const u = b.usuario || {}, p = b.perfil || {};
  const errores = {};
  const nombre = u.nombre !== undefined ? text(u.nombre, 80) : undefined;
  if (nombre !== undefined && (!nombre || nombre.length < 2)) errores.nombre = 'Escribe tu nombre';
  const paterno = u.apellido_paterno !== undefined ? text(u.apellido_paterno, 60) : undefined;
  if (paterno !== undefined && !paterno) errores.apellido_paterno = 'Escribe tu apellido paterno';
  if (u.telefono !== undefined && !phoneOk(u.telefono)) errores.telefono = 'Teléfono no válido';
  if (p.fecha_nacimiento && !/^\d{4}-\d{2}-\d{2}$/.test(p.fecha_nacimiento)) errores.fecha_nacimiento = 'Fecha no válida (AAAA-MM-DD)';
  if (p.fecha_nacimiento && new Date(p.fecha_nacimiento) > new Date()) errores.fecha_nacimiento = 'La fecha no puede ser futura';
  if (Object.keys(errores).length) throw bad('Revisa los datos', errores);

  const sets = [], vals = [];
  const add = (col, v) => { if (v !== undefined) { sets.push(`${col}=?`); vals.push(v); } };
  add('nombre', nombre); add('apellido_paterno', paterno);
  add('apellido_materno', u.apellido_materno !== undefined ? text(u.apellido_materno, 60) : undefined);
  add('telefono', u.telefono !== undefined ? text(u.telefono, 20) : undefined);

  await tx(async (conn) => {
    if (sets.length) await q(`UPDATE usuarios SET ${sets.join(',')} WHERE id_usuario=?`, [...vals, req.user.id_usuario], conn);
    const ps = [], pv = [];
    const padd = (col, v) => { if (v !== undefined) { ps.push(`${col}=?`); pv.push(v); } };
    padd('fecha_nacimiento', p.fecha_nacimiento !== undefined ? text(p.fecha_nacimiento, 10) : undefined);
    padd('direccion', p.direccion !== undefined ? text(p.direccion, 200) : undefined);
    padd('ciudad', p.ciudad !== undefined ? text(p.ciudad, 80) : undefined);
    padd('codigo_postal', p.codigo_postal !== undefined ? text(p.codigo_postal, 10) : undefined);
    padd('tipo_vivienda', p.tipo_vivienda !== undefined ? text(p.tipo_vivienda, 50) : undefined);
    padd('tiene_mascotas', p.tiene_mascotas !== undefined ? !!bool(p.tiene_mascotas) : undefined);
    padd('experiencia_mascotas', p.experiencia_mascotas !== undefined ? text(p.experiencia_mascotas, 2000) : undefined);
    padd('ocupacion', p.ocupacion !== undefined ? text(p.ocupacion, 80) : undefined);
    padd('contacto_emergencia_nombre', p.contacto_emergencia_nombre !== undefined ? text(p.contacto_emergencia_nombre, 120) : undefined);
    padd('contacto_emergencia_telefono', p.contacto_emergencia_telefono !== undefined ? text(p.contacto_emergencia_telefono, 20) : undefined);
    padd('contacto_emergencia_parentesco', p.contacto_emergencia_parentesco !== undefined ? text(p.contacto_emergencia_parentesco, 40) : undefined);
    if (ps.length) {
      await q('INSERT IGNORE INTO perfiles_adoptantes (id_usuario) VALUES (?)', [req.user.id_usuario], conn);
      await q(`UPDATE perfiles_adoptantes SET ${ps.join(',')} WHERE id_usuario=?`, [...pv, req.user.id_usuario], conn);
    }
  });
  res.json(await perfilCompleto(req, req.user.id_usuario));
}));

// Cambio de contraseña con sesión iniciada
router.put('/password', wrap(async (req, res) => {
  const { contrasena_actual: actual, nueva_contrasena: nueva } = req.body || {};
  const pe = passwordError(nueva); if (pe) throw bad(pe, { nueva_contrasena: pe });
  const u = await one('SELECT contrasena FROM usuarios WHERE id_usuario=?', [req.user.id_usuario]);
  if (typeof actual !== 'string' || !(await bcrypt.compare(actual, u.contrasena))) {
    throw bad('La contraseña actual no es correcta', { contrasena_actual: 'No es correcta' });
  }
  await q('UPDATE usuarios SET contrasena=? WHERE id_usuario=?', [await bcrypt.hash(nueva, 10), req.user.id_usuario]);
  res.json({ mensaje: 'Contraseña actualizada' });
}));

// RF004 — Foto de perfil
router.post('/foto', upload.single('foto'), wrap(async (req, res) => {
  const kind = validar(req.file, ['jpg', 'png'], 5);
  const prev = await one('SELECT foto_perfil FROM usuarios WHERE id_usuario=?', [req.user.id_usuario]);
  const { ruta } = guardarPublico('perfiles', req.file.buffer, kind.ext);
  await q('UPDATE usuarios SET foto_perfil=? WHERE id_usuario=?', [ruta, req.user.id_usuario]);
  if (prev?.foto_perfil && prev.foto_perfil.startsWith('perfiles/') && !/org_/.test(prev.foto_perfil)) borrarPublico(prev.foto_perfil);
  res.json({ foto_perfil: fileUrl(req, ruta) });
}));

// RF005 — Preferencias de búsqueda
router.get('/preferencias', wrap(async (req, res) => {
  res.json((await one('SELECT especie,tamano,edad_preferida FROM preferencias_usuario WHERE id_usuario=? ORDER BY id_preferencia DESC LIMIT 1', [req.user.id_usuario]))
    || { especie: null, tamano: null, edad_preferida: null });
}));
router.put('/preferencias', wrap(async (req, res) => {
  const b = req.body || {};
  const tamano = text(b.tamano, 30), edad = text(b.edad_preferida, 30), especie = text(b.especie, 50);
  if (tamano && !['Pequeno', 'Mediano', 'Grande'].includes(tamano)) throw bad('Tamaño no válido');
  await tx(async (conn) => {
    await q('DELETE FROM preferencias_usuario WHERE id_usuario=?', [req.user.id_usuario], conn);
    if (especie || tamano || edad) {
      await q('INSERT INTO preferencias_usuario (id_usuario,especie,tamano,edad_preferida) VALUES (?,?,?,?)', [req.user.id_usuario, especie, tamano, edad], conn);
    }
  });
  res.json({ especie, tamano, edad_preferida: edad });
}));

module.exports = router;
