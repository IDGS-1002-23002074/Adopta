// Crea adopta_db desde db/Adopta_DB_completa.sql (¡borra la base existente!).
const fs = require('fs');
const path = require('path');
const mysql = require('mysql2/promise');
const cfg = require('../src/config');

(async () => {
  const sql = fs.readFileSync(path.join(__dirname, '..', 'db', 'Adopta_DB_completa.sql'), 'utf8');
  const conn = await mysql.createConnection({ host: cfg.db.host, port: cfg.db.port, user: cfg.db.user, password: cfg.db.password, multipleStatements: true });
  await conn.query(sql);
  const [r] = await conn.query("SELECT COUNT(*) AS n FROM information_schema.tables WHERE table_schema='adopta_db' AND table_type='BASE TABLE'");
  console.log(`adopta_db creada: ${r[0].n} tablas`);
  await conn.end();
})().catch((e) => { console.error('Error creando la BD:', e.message); process.exit(1); });
