
const router = require('express').Router();
const bcrypt = require('bcryptjs');
const crypto = require('crypto');
const rateLimit = require('express-rate-limit');
const cfg = require('../config');
const { q, one, tx } = require('../db');
const cat = require('../catalogos');
const { firmar } = require('../middleware/auth');
const { sendMail } = require('../services');
const {
  HttpError, bad, wrap, isEmail, passwordError,
  phoneOk, text, maskEmail
} = require('../utils');

const limiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: 30,
  standardHeaders: true,
  legacyHeaders: false,
  message: {
    error: 'Demasiados intentos. Espera unos minutos e inténtalo de nuevo.'
  }
});

const VIGENCIA_MINUTOS = 10;
const MAX_INTENTOS = 5;
const ESPERA_REENVIO_SEGUNDOS = 60;

async function usuarioPublico(id) {
  return one(
    `SELECT u.id_usuario,u.nombre,u.apellido_paterno,u.apellido_materno,
            u.correo,u.telefono,r.nombre AS rol
     FROM usuarios u
     JOIN roles r ON r.id_rol=u.id_rol
     WHERE u.id_usuario=?`,
    [id]
  );
}

// Genera un código aleatorio de seis dígitos.
function generarCodigo() {
  return String(crypto.randomInt(100000, 1000000));
}

// Guarda únicamente un hash HMAC del código.
function hashCodigo(idUsuario, codigo) {
  return crypto
    .createHmac('sha256', cfg.jwtSecret)
    .update(`${idUsuario}|${codigo}`)
    .digest('hex');
}

function compararCodigo(idUsuario, codigo, hashGuardado) {
  const hashRecibido = Buffer.from(
    hashCodigo(idUsuario, codigo),
    'hex'
  );

  const hashOriginal = Buffer.from(hashGuardado, 'hex');

  return hashRecibido.length === hashOriginal.length &&
    crypto.timingSafeEqual(hashRecibido, hashOriginal);
}

async function enviarCodigoVerificacion(correo, nombre, codigo) {
  const enviado = await sendMail(
    correo,
    'Verifica tu correo - Adopta+',
    `
      <div style="font-family:Arial,sans-serif;color:#333">
        <h2>¡Bienvenido a Adopta+!</h2>
        <p>Hola ${nombre},</p>
        <p>Para activar tu cuenta, ingresa el siguiente código:</p>
        <p style="font-size:28px;font-weight:bold;letter-spacing:6px">
          ${codigo}
        </p>
        <p>Este código vence en ${VIGENCIA_MINUTOS} minutos.</p>
        <p>Si no realizaste este registro, puedes ignorar este mensaje.</p>
      </div>
    `
  );

  if (!enviado) {
    throw new HttpError(
      503,
      'No fue posible enviar el correo de verificación. Solicita otro código más tarde.'
    );
  }
}

// RF002 — Registro de adoptante.
router.post('/register', limiter, wrap(async (req, res) => {
  const b = req.body || {};
  const errores = {};

  const nombre = text(b.nombre, 80);
  const paterno = text(b.apellido_paterno, 60);
  const materno = text(b.apellido_materno, 60);
  const correo = text(b.correo, 120)?.toLowerCase();
  const telefono = text(b.telefono, 20);

  if (!nombre || nombre.length < 2) {
    errores.nombre = 'Escribe tu nombre';
  }

  if (!paterno) {
    errores.apellido_paterno = 'Escribe tu apellido paterno';
  }

  if (!isEmail(correo)) {
    errores.correo = 'Correo electrónico no válido';
  }

  const pe = passwordError(b.contrasena);
  if (pe) {
    errores.contrasena = pe;
  }

  if (!phoneOk(telefono)) {
    errores.telefono = 'Teléfono no válido';
  }

  if (Object.keys(errores).length) {
    throw bad('Revisa los datos del formulario', errores);
  }

  const hash = await bcrypt.hash(b.contrasena, 10);
  const idRol = await cat.id('rol', 'Adoptante');
  const codigo = generarCodigo();

  const id = await tx(async (conn) => {
    const duplicado = await one(
      'SELECT id_usuario, estado FROM usuarios WHERE correo=?',
      [correo],
      conn
    );

    if (duplicado) {
      throw new HttpError(
        409,
        'Ya existe una cuenta con ese correo',
        { correo: 'Ya está registrado' }
      );
    }

    const resultado = await q(
      `INSERT INTO usuarios
       (id_rol,nombre,apellido_paterno,apellido_materno,
        correo,contrasena,telefono,estado)
       VALUES (?,?,?,?,?,?,?,'Pendiente')`,
      [idRol, nombre, paterno, materno, correo, hash, telefono],
      conn
    );

    await q(
      'INSERT INTO perfiles_adoptantes (id_usuario) VALUES (?)',
      [resultado.insertId],
      conn
    );

    await q(
      `INSERT INTO verificaciones_correo
       (id_usuario,codigo_hash,fecha_expiracion,intentos)
       VALUES (?,?,DATE_ADD(NOW(), INTERVAL ? MINUTE),0)`,
      [
        resultado.insertId,
        hashCodigo(resultado.insertId, codigo),
        VIGENCIA_MINUTOS
      ],
      conn
    );

    return resultado.insertId;
  });

  // El usuario permanece Pendiente si ocurre un error al enviar el correo.
  await enviarCodigoVerificacion(correo, nombre, codigo);

  res.status(201).json({
    mensaje: 'Registro creado. Revisa tu correo e ingresa el código de verificación.',
    correo_enmascarado: maskEmail(correo),
    vigencia_minutos: VIGENCIA_MINUTOS
  });
}));

// Verificación del correo electrónico.
router.post('/verify-email', limiter, wrap(async (req, res) => {
  const correo = text(req.body?.correo, 120)?.toLowerCase();
  const codigo = String(req.body?.codigo || '').trim();

  if (!isEmail(correo) || !/^\d{6}$/.test(codigo)) {
    throw bad('Ingresa un correo válido y el código de seis dígitos.');
  }

  const usuario = await one(
    `SELECT u.id_usuario,u.estado,v.codigo_hash,
            v.fecha_expiracion,v.intentos
     FROM usuarios u
     JOIN verificaciones_correo v ON v.id_usuario=u.id_usuario
     WHERE u.correo=?`,
    [correo]
  );

  if (!usuario || usuario.estado !== 'Pendiente') {
    throw new HttpError(400, 'No existe una verificación pendiente para esta cuenta.');
  }

  if (usuario.intentos >= MAX_INTENTOS) {
    throw new HttpError(
      429,
      'Superaste el número de intentos. Solicita un nuevo código.'
    );
  }

  const expiracion = new Date(usuario.fecha_expiracion);

  if (Number.isNaN(expiracion.getTime()) || expiracion.getTime() <= Date.now()) {
    throw new HttpError(
      400,
      'El código venció. Solicita uno nuevo.'
    );
  }

  const valido = compararCodigo(
    usuario.id_usuario,
    codigo,
    usuario.codigo_hash
  );

  if (!valido) {
    await q(
      `UPDATE verificaciones_correo
       SET intentos=intentos+1
       WHERE id_usuario=?`,
      [usuario.id_usuario]
    );

    throw new HttpError(400, 'El código de verificación es incorrecto.');
  }

  await tx(async (conn) => {
    await q(
      `UPDATE usuarios
       SET estado='Activo'
       WHERE id_usuario=? AND estado='Pendiente'`,
      [usuario.id_usuario],
      conn
    );

    await q(
      'DELETE FROM verificaciones_correo WHERE id_usuario=?',
      [usuario.id_usuario],
      conn
    );
  });

  res.json({
    mensaje: 'Correo verificado correctamente. Ya puedes iniciar sesión.'
  });
}));

// Reenvío del código de verificación.
router.post('/resend-verification', limiter, wrap(async (req, res) => {
  const correo = text(req.body?.correo, 120)?.toLowerCase();

  if (!isEmail(correo)) {
    throw bad('Correo electrónico no válido.');
  }

  const usuario = await one(
    `SELECT u.id_usuario,u.nombre,u.estado,
            v.fecha_creacion
     FROM usuarios u
     LEFT JOIN verificaciones_correo v
       ON v.id_usuario=u.id_usuario
     WHERE u.correo=?`,
    [correo]
  );

  // Respuesta genérica para no revelar si una cuenta existe.
  const respuesta = {
    mensaje: 'Si existe una cuenta pendiente, recibirás un nuevo código.',
    correo_enmascarado: maskEmail(correo),
    vigencia_minutos: VIGENCIA_MINUTOS
  };

  if (!usuario || usuario.estado !== 'Pendiente') {
    return res.json(respuesta);
  }

  if (!usuario.fecha_creacion) {
    return res.json(respuesta);
  }

  const ultimaCreacion = new Date(usuario.fecha_creacion).getTime();
  const segundosTranscurridos = (Date.now() - ultimaCreacion) / 1000;

  if (segundosTranscurridos < ESPERA_REENVIO_SEGUNDOS) {
    const restantes = Math.ceil(
      ESPERA_REENVIO_SEGUNDOS - segundosTranscurridos
    );

    throw new HttpError(
      429,
      `Espera ${restantes} segundos antes de solicitar otro código.`
    );
  }

  const codigo = generarCodigo();

  await q(
    `UPDATE verificaciones_correo
     SET codigo_hash=?,
         fecha_expiracion=DATE_ADD(NOW(), INTERVAL ? MINUTE),
         intentos=0,
         fecha_creacion=CURRENT_TIMESTAMP
     WHERE id_usuario=?`,
    [
      hashCodigo(usuario.id_usuario, codigo),
      VIGENCIA_MINUTOS,
      usuario.id_usuario
    ]
  );

  await enviarCodigoVerificacion(
    correo,
    usuario.nombre,
    codigo
  );

  res.json(respuesta);
}));

// RF001 — Inicio de sesión.
router.post('/login', limiter, wrap(async (req, res) => {
  const correo = text(req.body?.correo, 120)?.toLowerCase();
  const pass = req.body?.contrasena;

  if (!isEmail(correo) || typeof pass !== 'string' || !pass) {
    throw bad('Ingresa tu correo y contraseña');
  }

  const u = await one(
    `SELECT u.id_usuario,u.contrasena,u.estado,r.nombre AS rol
     FROM usuarios u
     JOIN roles r ON r.id_rol=u.id_rol
     WHERE u.correo=?`,
    [correo]
  );

  const ok = u && await bcrypt.compare(pass, u.contrasena);

  if (!ok) {
    throw new HttpError(401, 'Correo o contraseña incorrectos');
  }

  if (u.estado === 'Pendiente') {
    throw new HttpError(
      403,
      'Debes verificar tu correo electrónico antes de iniciar sesión.'
    );
  }

  if (u.estado !== 'Activo') {
    throw new HttpError(
      403,
      `Tu cuenta está ${u.estado.toLowerCase()}. Contacta a soporte`
    );
  }

  const usuario = await usuarioPublico(u.id_usuario);

  res.json({
    token: firmar(usuario, usuario.rol),
    usuario
  });
}));

// RF003 — Recuperación de contraseña.
// Código HMAC de seis dígitos ligado a la contraseña actual.
const VENTANA_MS = 10 * 60 * 1000;

function codigoRecuperacion(u, ventana) {
  const h = crypto
    .createHmac('sha256', cfg.jwtSecret)
    .update(`${u.id_usuario}|${u.contrasena}|${ventana}`)
    .digest('hex');

  return String(
    parseInt(h.slice(0, 8), 16) % 1000000
  ).padStart(6, '0');
}

const codigoRecuperacionValido = (u, codigo) => {
  const ventanaActual = Math.floor(Date.now() / VENTANA_MS);

  return [ventanaActual, ventanaActual - 1].some(
    (ventana) => codigoRecuperacion(u, ventana) === String(codigo)
  );
};

router.post('/forgot', limiter, wrap(async (req, res) => {
  const correo = text(req.body?.correo, 120)?.toLowerCase();

  if (!isEmail(correo)) {
    throw bad(
      'Correo electrónico no válido',
      { correo: 'Correo electrónico no válido' }
    );
  }

  const u = await one(
    `SELECT id_usuario, estado, nombre
     FROM usuarios
     WHERE correo = ?`,
    [correo]
  );

  const out = {
    mensaje: 'Si el correo está registrado, te enviamos un código de verificación.',
    correo_enmascarado: maskEmail(correo),
    vigencia_minutos: VIGENCIA_MINUTOS
  };

  if (u && u.estado === 'Activo') {
    const codigo = generarCodigo();
    const codigoHash = hashCodigo(u.id_usuario, codigo);

    // Invalidar cualquier código anterior que siga activo.
    await q(
      `UPDATE tokens_recuperacion
       SET usado = 1
       WHERE id_usuario = ? AND usado = 0`,
      [u.id_usuario]
    );

    // Guardar únicamente el hash del nuevo código.
    await q(
      `INSERT INTO tokens_recuperacion
       (id_usuario, token_hash, fecha_creacion, fecha_expiracion, usado, intentos)
       VALUES (?, ?, NOW(), DATE_ADD(NOW(), INTERVAL ? MINUTE), 0, 0)`,
      [u.id_usuario, codigoHash, VIGENCIA_MINUTOS]
    );

    try {
      const enviado = await sendMail(
        correo,
        'Tu código de recuperación de Adopta+',
        `
          <p>Hola ${u.nombre},</p>
          <p>Recibimos una solicitud para restablecer tu contraseña.</p>
          <p>Tu código de verificación es:</p>
          <p style="font-size:24px;font-weight:bold;letter-spacing:5px">
            ${codigo}
          </p>
          <p>Este código vence en ${VIGENCIA_MINUTOS} minutos.</p>
          <p>Si no solicitaste este cambio, puedes ignorar este mensaje.</p>
        `
      );

      if (!enviado) {
        throw new Error('El servicio de correo no pudo enviar el código.');
      }
    } catch (error) {
      // Si no se pudo enviar, invalidar el código que quedó guardado.
      await q(
        `UPDATE tokens_recuperacion
         SET usado = 1
         WHERE id_usuario = ? AND token_hash = ?`,
        [u.id_usuario, codigoHash]
      );

      console.error('Error enviando código de recuperación:', error.message);
      throw new HttpError(503, 'No fue posible enviar el correo. Inténtalo nuevamente.');
    }
  }

  res.json(out);
}));

router.post('/reset', limiter, wrap(async (req, res) => {
  const correo = text(req.body?.correo, 120)?.toLowerCase();
  const { codigo, nueva_contrasena: nueva } = req.body || {};

  const pe = passwordError(nueva);

  if (pe) {
    throw bad(pe, { nueva_contrasena: pe });
  }

  if (!isEmail(correo) || !/^\d{6}$/.test(String(codigo || ''))) {
    throw new HttpError(400, 'El código no es válido o ya venció');
  }

  const u = await one(
    `SELECT
       u.id_usuario,
       u.estado,
       t.token_hash,
       t.usado,
       t.intentos,
       (t.fecha_expiracion > NOW()) AS vigente
     FROM usuarios u
     INNER JOIN tokens_recuperacion t
       ON t.id_usuario = u.id_usuario
     WHERE u.correo = ?
     ORDER BY t.fecha_creacion DESC
     LIMIT 1`,
    [correo]
  );

  if (!u || u.estado !== 'Activo') {
    throw new HttpError(400, 'El código no es válido o ya venció');
  }

  if (Number(u.usado) === 1 || Number(u.vigente) !== 1) {
    throw new HttpError(400, 'El código no es válido o ya venció');
  }

  if (Number(u.intentos) >= MAX_INTENTOS) {
    await q(
      `UPDATE tokens_recuperacion
       SET usado = 1
       WHERE id_usuario = ? AND token_hash = ?`,
      [u.id_usuario, u.token_hash]
    );

    throw new HttpError(
      400,
      'Superaste el número de intentos. Solicita un código nuevo.'
    );
  }

  // Comparar el código recibido contra el hash almacenado.
  if (!compararCodigo(u.id_usuario, codigo, u.token_hash)) {
    await q(
      `UPDATE tokens_recuperacion
       SET intentos = intentos + 1,
           usado = IF(intentos + 1 >= ?, 1, usado)
       WHERE id_usuario = ? AND token_hash = ?`,
      [MAX_INTENTOS, u.id_usuario, u.token_hash]
    );

    throw new HttpError(400, 'El código no es válido o ya venció');
  }

  // Actualizar la contraseña y consumir el código.
  const hashContrasena = await bcrypt.hash(nueva, 10);

  await q(
    `UPDATE usuarios
     SET contrasena = ?
     WHERE id_usuario = ?`,
    [hashContrasena, u.id_usuario]
  );

  await q(
    `UPDATE tokens_recuperacion
     SET usado = 1
     WHERE id_usuario = ? AND usado = 0`,
    [u.id_usuario]
  );

  res.json({
    mensaje: 'Contraseña actualizada. Ya puedes iniciar sesión.'
  });
}));

module.exports = router;
