// Validación y almacenamiento de archivos (RNF017).
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const multer = require('multer');
const cfg = require('./config');
const { HttpError } = require('./utils');

const MAX_BYTES = 5 * 1024 * 1024;
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: MAX_BYTES, files: 6, fields: 30 }
});

/** Detecta el tipo real por los primeros bytes (no confía en la extensión que manda el cliente). */
function sniff(buf) {
  if (!buf || buf.length < 12) return null;
  if (buf.slice(0, 4).toString('latin1') === '%PDF') return { ext: 'pdf', mime: 'application/pdf' };
  if (buf[0] === 0xff && buf[1] === 0xd8 && buf[2] === 0xff) return { ext: 'jpg', mime: 'image/jpeg' };
  if (buf.slice(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) return { ext: 'png', mime: 'image/png' };
  return null;
}

/** Verifica que el archivo sea de un formato permitido; devuelve {ext,mime}. */
function validar(file, permitidos = ['jpg', 'png'], maxMb = 5) {
  if (!file) throw new HttpError(422, 'Falta el archivo');
  const kind = sniff(file.buffer);
  if (!kind || !permitidos.includes(kind.ext)) {
    throw new HttpError(422, `Formato no permitido. Usa: ${permitidos.join(', ').toUpperCase()}`);
  }
  if (file.size > maxMb * 1024 * 1024) throw new HttpError(422, `El archivo supera ${maxMb} MB`);
  return kind;
}

/** Guarda un buffer con nombre UUID. root = uploadsDir (público) o privateDir. */
function guardar(root, subdir, buffer, ext) {
  const dir = path.join(root, subdir);
  fs.mkdirSync(dir, { recursive: true });
  const nombre = `${crypto.randomUUID()}.${ext}`;
  fs.writeFileSync(path.join(dir, nombre), buffer);
  return { nombre, ruta: `${subdir}/${nombre}`.replace(/\\/g, '/') };
}
const guardarPublico = (subdir, buffer, ext) => guardar(cfg.uploadsDir, subdir, buffer, ext);
const guardarPrivado = (subdir, buffer, ext) => guardar(cfg.privateDir, subdir, buffer, ext);
const sha256 = (buf) => crypto.createHash('sha256').update(buf).digest('hex');
function borrarPublico(ruta) {
  if (!ruta || /^https?:/.test(ruta)) return;
  const full = path.join(cfg.uploadsDir, ruta);
  if (full.startsWith(cfg.uploadsDir)) fs.rm(full, { force: true }, () => {});
}

module.exports = { upload, validar, guardarPublico, guardarPrivado, sha256, borrarPublico };
