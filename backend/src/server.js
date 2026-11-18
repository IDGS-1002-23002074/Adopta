const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const fs = require('fs');
const cfg = require('./config');
const { pool } = require('./db');
const { auth, soloRol } = require('./middleware/auth');

const app = express();
app.set('trust proxy', 1);
app.use(helmet({ crossOriginResourcePolicy: { policy: 'cross-origin' } }));
app.use(cors());
app.use(express.json({ limit: '1mb' }));

for (const d of [cfg.uploadsDir, cfg.privateDir]) fs.mkdirSync(d, { recursive: true });
app.use('/uploads', express.static(cfg.uploadsDir, { maxAge: '1h', index: false }));

app.get('/api/health', async (req, res) => {
  try { await pool.query('SELECT 1'); res.json({ ok: true, db: 'conectada' }); }
  catch (e) { res.status(503).json({ ok: false, db: 'sin conexión' }); }
});

// Autenticación (pública)
app.use('/api/auth', require('./routes/auth'));

// Todo lo demás exige sesión de adoptante (RNF001). Las organizaciones y el administrador usan el portal web.
const sol = require('./routes/solicitudes');
const api = express.Router();
api.use(auth, soloRol('Adoptante'));
api.use('/me', require('./routes/me'));
api.use('/solicitudes', sol.router);
api.use('/archivos', sol.archivos);
api.use('/notificaciones', require('./routes/notificaciones'));
api.use(require('./routes/mascotas'));
api.use(require('./routes/adopciones'));
api.use(require('./routes/comunidad'));
app.use('/api', api);

app.use('/api', (req, res) => res.status(404).json({ error: 'Ruta no encontrada' }));

// Manejo de errores uniforme (RNF009, RNF013)
app.use((err, req, res, next) => { // eslint-disable-line no-unused-vars
  if (err.name === 'MulterError') {
    const msg = err.code === 'LIMIT_FILE_SIZE' ? 'El archivo supera los 5 MB' : 'Archivos no válidos';
    return res.status(413).json({ error: msg });
  }
  if (err.type === 'entity.parse.failed') return res.status(400).json({ error: 'JSON mal formado' });
  if (err.status) return res.status(err.status).json({ error: err.message, detalles: err.details });
  if (err.code === 'ER_DUP_ENTRY') return res.status(409).json({ error: 'El registro ya existe' });
  console.error(err);
  res.status(500).json({ error: 'Ocurrió un error en el servidor. Inténtalo de nuevo.' });
});

if (require.main === module) {
  app.listen(cfg.port, '0.0.0.0', () => console.log(`Adopta+ API en http://localhost:${cfg.port}  (BD: ${cfg.db.database}@${cfg.db.host})`));
}
module.exports = app;
