const cfg = require('./config');

class HttpError extends Error {
  constructor(status, message, details) {
    super(message);
    this.status = status;
    this.details = details;
  }
}
const bad = (msg, details) => new HttpError(422, msg, details);
const notFound = (msg = 'No encontrado') => new HttpError(404, msg);
const conflict = (msg) => new HttpError(409, msg);
const forbidden = (msg = 'No tienes permiso para esta operación') => new HttpError(403, msg);

const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

/** Convierte una ruta guardada en BD en URL absoluta. */
function baseUrl(req) {
  return cfg.publicBaseUrl || `${req.protocol}://${req.get('host')}`;
}
function fileUrl(req, ruta) {
  if (!ruta) return null;
  if (/^https?:\/\//i.test(ruta)) return ruta;
  return `${baseUrl(req)}/uploads/${String(ruta).replace(/^\/+/, '')}`;
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
const isEmail = (v) => typeof v === 'string' && EMAIL_RE.test(v.trim()) && v.length <= 120;
const passwordError = (v) => {
  if (typeof v !== 'string' || v.length < 8) return 'La contraseña debe tener al menos 8 caracteres';
  if (v.length > 72) return 'La contraseña es demasiado larga';
  if (!/[A-Za-z]/.test(v) || !/\d/.test(v)) return 'La contraseña debe incluir letras y números';
  return null;
};
const phoneOk = (v) => v == null || v === '' || /^[0-9+\s()-]{7,20}$/.test(String(v));

/** Texto limpio o null. */
function text(v, max = 255) {
  if (v === undefined || v === null) return null;
  const s = String(v).trim();
  return s === '' ? null : s.slice(0, max);
}
function bool(v) {
  if (typeof v === 'boolean') return v;
  if (v === 1 || v === '1' || v === 'true') return true;
  if (v === 0 || v === '0' || v === 'false') return false;
  return null;
}
function int(v) {
  if (v === undefined || v === null || v === '') return null;
  const n = Number(v);
  return Number.isInteger(n) ? n : null;
}
function pagination(req, def = 20, max = 50) {
  const limit = Math.min(Math.max(int(req.query.limit) || def, 1), max);
  const offset = Math.max(int(req.query.offset) || 0, 0);
  return { limit, offset };
}
function maskEmail(correo) {
  const [u, d] = String(correo).split('@');
  return `${'*'.repeat(Math.max(u.length - 3, 3))}${u.slice(-3)}@${d}`;
}

module.exports = {
  HttpError, bad, notFound, conflict, forbidden, wrap, fileUrl, baseUrl,
  isEmail, passwordError, phoneOk, text, bool, int, pagination, maskEmail
};
