
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

const env = (k, d) =>
  process.env[k] !== undefined && process.env[k] !== ''
    ? process.env[k]
    : d;

const root = path.join(__dirname, '..');

const cfg = {
  env: env('NODE_ENV', 'development'),
  port: Number(env('PORT', 3000)),

  db: {
    host: env('DB_HOST', '127.0.0.1'),
    port: Number(env('DB_PORT', 3306)),
    user: env('DB_USER', 'root'),
    password: env('DB_PASSWORD', ''),
    database: env('DB_NAME', 'adopta_db'),

    // SSL para Azure MySQL
    ssl: env('DB_SSL', 'false') === 'true'
      ? { minVersion: 'TLSv1.2', rejectUnauthorized: false }
      : undefined
  },

  jwtSecret: env(
    'JWT_SECRET',
    'cambia-este-secreto-por-uno-largo-y-aleatorio'
  ),

  jwtExpires: env('JWT_EXPIRES', '7d'),

  publicBaseUrl: env('PUBLIC_BASE_URL', '').replace(/\/$/, ''),

  uploadsDir: path.resolve(
    env('UPLOADS_DIR', path.join(root, 'uploads'))
  ),

  privateDir: path.resolve(
    env('PRIVATE_DIR', path.join(root, 'uploads_private'))
  ),

  autoSolicitarDocumentos:
    env('AUTO_SOLICITAR_DOCUMENTOS', 'true') === 'true',

  diasLimiteDocumentos: Number(env('DIAS_LIMITE_DOCUMENTOS', 15)),

  maxSolicitudesActivas: 3,

  devExposeResetCode:
    env('DEV_EXPOSE_RESET_CODE', 'true') === 'true',

  smtp: {
    host: env('SMTP_HOST', ''),
    port: Number(env('SMTP_PORT', 587)),
    user: env('SMTP_USER', ''),
    pass: env('SMTP_PASS', ''),
    from: env('SMTP_FROM', 'Adopta+ <no-responder@adopta.com>')
  }
};

if (
  cfg.env === 'production' &&
  cfg.jwtSecret.startsWith('cambia-este-secreto')
) {
  throw new Error('Define JWT_SECRET en producción (RNF016).');
}

module.exports = cfg;
