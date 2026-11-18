const jwt = require('jsonwebtoken');
const cfg = require('../config');
const { one } = require('../db');
const { HttpError, wrap } = require('../utils');

function firmar(usuario, rol) {
  return jwt.sign({ sub: usuario.id_usuario, rol }, cfg.jwtSecret, { expiresIn: cfg.jwtExpires });
}

/** Exige sesión válida y cuenta Activa (RNF001, RNF016). */
const auth = wrap(async (req, res, next) => {
  const h = req.get('authorization') || '';
  const token = h.startsWith('Bearer ') ? h.slice(7) : null;
  if (!token) throw new HttpError(401, 'Inicia sesión para continuar');
  let payload;
  try {
    payload = jwt.verify(token, cfg.jwtSecret);
  } catch (e) {
    throw new HttpError(401, e.name === 'TokenExpiredError' ? 'Tu sesión expiró. Inicia sesión de nuevo' : 'Sesión inválida');
  }
  const u = await one(
    `SELECT u.id_usuario,u.nombre,u.apellido_paterno,u.correo,u.estado,r.nombre AS rol
     FROM usuarios u JOIN roles r ON r.id_rol=u.id_rol WHERE u.id_usuario=?`, [payload.sub]);
  if (!u) throw new HttpError(401, 'Sesión inválida');
  if (u.estado !== 'Activo') throw new HttpError(403, `Tu cuenta está ${u.estado.toLowerCase()}`);
  req.user = u;
  next();
});

/** Restringe a ciertos roles. */
const soloRol = (...roles) => (req, res, next) =>
  roles.includes(req.user.rol) ? next() : next(new HttpError(403, 'Tu rol no tiene acceso a esta función'));

module.exports = { auth, soloRol, firmar };
