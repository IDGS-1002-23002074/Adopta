const router = require('express').Router();
const bcrypt = require('bcryptjs');
const crypto = require('crypto');
const rateLimit = require('express-rate-limit');
const cfg = require('../config');
const { q, one, tx } = require('../db');
const cat = require('../catalogos');
const { firmar } = require('../middleware/auth');
const { sendMail } = require('../services');
const { HttpError, bad, wrap, isEmail, passwordError, phoneOk, text, maskEmail } = require('../utils');

const limiter = rateLimit({
  windowMs: 15 * 60 * 1000, limit: 30, standardHeaders: true, legacyHeaders: false,
  message: { error: 'Demasiados intentos. Espera unos minutos e inténtalo de nuevo.' }
});

async function usuarioPublico(id) {
  return one(
    `SELECT u.id_usuario,u.nombre,u.apellido_paterno,u.apellido_materno,u.correo,u.telefono,r.nombre AS rol
     FROM usuarios u JOIN roles r ON r.id_rol=u.id_rol WHERE u.id_usuario=?`, [id]);
}

// RF002 — Registro de adoptante
router.post('/register', limiter, wrap(async (req, res) => {
  const b = req.body || {};
  const errores = {};
  const nombre = text(b.nombre, 80), paterno = text(b.apellido_paterno, 60), materno = text(b.apellido_materno, 60);
  const correo = text(b.correo, 120)?.toLowerCase(), telefono = text(b.telefono, 20);
  if (!nombre || nombre.length < 2) errores.nombre = 'Escribe tu nombre';
  if (!paterno) errores.apellido_paterno = 'Escribe tu apellido paterno';
  if (!isEmail(correo)) errores.correo = 'Correo electrónico no válido';
  const pe = passwordError(b.contrasena); if (pe) errores.contrasena = pe;
  if (!phoneOk(telefono)) errores.telefono = 'Teléfono no válido';
  if (Object.keys(errores).length) throw bad('Revisa los datos del formulario', errores);

  const hash = await bcrypt.hash(b.contrasena, 10);
  const idRol = await cat.id('rol', 'Adoptante');
  const id = await tx(async (conn) => {
    const dup = await one('SELECT 1 AS x FROM usuarios WHERE correo=?', [correo], conn);
    if (dup) throw new HttpError(409, 'Ya existe una cuenta con ese correo', { correo: 'Ya está registrado' });
    const r = await q(
      'INSERT INTO usuarios (id_rol,nombre,apellido_paterno,apellido_materno,correo,contrasena,telefono) VALUES (?,?,?,?,?,?,?)',
      [idRol, nombre, paterno, materno, correo, hash, telefono], conn);
    await q('INSERT INTO perfiles_adoptantes (id_usuario) VALUES (?)', [r.insertId], conn);
    return r.insertId;
  });
  const usuario = await usuarioPublico(id);
  res.status(201).json({ token: firmar(usuario, usuario.rol), usuario });
}));

// RF001 — Inicio de sesión (identifica el rol)
router.post('/login', limiter, wrap(async (req, res) => {
  const correo = text(req.body?.correo, 120)?.toLowerCase();
  const pass = req.body?.contrasena;
  if (!isEmail(correo) || typeof pass !== 'string' || !pass) throw bad('Ingresa tu correo y contraseña');
  const u = await one(
    `SELECT u.id_usuario,u.contrasena,u.estado,r.nombre AS rol FROM usuarios u
     JOIN roles r ON r.id_rol=u.id_rol WHERE u.correo=?`, [correo]);
  // Misma respuesta si no existe o la contraseña falla (no revela cuentas)
  const ok = u && (await bcrypt.compare(pass, u.contrasena));
  if (!ok) throw new HttpError(401, 'Correo o contraseña incorrectos');
  if (u.estado !== 'Activo') throw new HttpError(403, `Tu cuenta está ${u.estado.toLowerCase()}. Contacta a soporte`);
  const usuario = await usuarioPublico(u.id_usuario);
  res.json({ token: firmar(usuario, usuario.rol), usuario });
}));

// RF003 — Recuperación de contraseña sin tabla extra: código HMAC de 6 dígitos ligado a la contraseña actual
const VENTANA_MS = 10 * 60 * 1000;
function codigo(u, ventana) {
  const h = crypto.createHmac('sha256', cfg.jwtSecret).update(`${u.id_usuario}|${u.contrasena}|${ventana}`).digest('hex');
  return String(parseInt(h.slice(0, 8), 16) % 1000000).padStart(6, '0');
}
const codigoValido = (u, c) => {
  const w = Math.floor(Date.now() / VENTANA_MS);
  return [w, w - 1].some((x) => codigo(u, x) === String(c));
};

router.post('/forgot', limiter, wrap(async (req, res) => {
  const correo = text(req.body?.correo, 120)?.toLowerCase();
  if (!isEmail(correo)) throw bad('Correo electrónico no válido', { correo: 'Correo electrónico no válido' });
  const u = await one('SELECT id_usuario,contrasena,estado,nombre FROM usuarios WHERE correo=?', [correo]);
  const out = { mensaje: 'Si el correo está registrado, te enviamos un código de verificación.', correo_enmascarado: maskEmail(correo), vigencia_minutos: 10 };
  if (u && u.estado === 'Activo') {
    const c = codigo(u, Math.floor(Date.now() / VENTANA_MS));
    sendMail(correo, 'Tu código de Adopta+', `<p>Hola ${u.nombre},</p><p>Tu código para restablecer la contraseña es <b style="font-size:20px">${c}</b>. Vence en 10 minutos.</p><p>Si no lo solicitaste, ignora este mensaje.</p>`)
      .catch((e) => console.error('Error enviando correo:', e.message));
    if (cfg.devExposeResetCode && cfg.env !== 'production') out.codigo_dev = c;
  }
  res.json(out);
}));

router.post('/reset', limiter, wrap(async (req, res) => {
  const correo = text(req.body?.correo, 120)?.toLowerCase();
  const { codigo: c, nueva_contrasena: nueva } = req.body || {};
  const pe = passwordError(nueva); if (pe) throw bad(pe, { nueva_contrasena: pe });
  const u = await one('SELECT id_usuario,contrasena,estado FROM usuarios WHERE correo=?', [correo || '']);
  if (!u || u.estado !== 'Activo' || !/^\d{6}$/.test(String(c)) || !codigoValido(u, c)) {
    throw new HttpError(400, 'El código no es válido o ya venció');
  }
  await q('UPDATE usuarios SET contrasena=? WHERE id_usuario=?', [await bcrypt.hash(nueva, 10), u.id_usuario]);
  res.json({ mensaje: 'Contraseña actualizada. Ya puedes iniciar sesión.' });
}));

module.exports = router;
