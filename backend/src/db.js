const mysql = require('mysql2/promise');
const cfg = require('./config');

const pool = mysql.createPool({
  ...cfg.db,
  waitForConnections: true,
  connectionLimit: 10,
  charset: 'utf8mb4',
  dateStrings: true,        // DATETIME como 'YYYY-MM-DD HH:mm:ss'
  decimalNumbers: true,     // DECIMAL como número
  typeCast(field, next) {   // BOOLEAN (TINYINT(1)) como true/false/null
    if (field.type === 'TINY' && field.length === 1) {
      const v = field.string();
      return v === null ? null : v === '1';
    }
    return next();
  }
});

/** Ejecuta una consulta y devuelve las filas. */
async function q(sql, params = [], conn = pool) {
  const [rows] = await conn.query(sql, params);
  return rows;
}
async function one(sql, params = [], conn = pool) {
  const rows = await q(sql, params, conn);
  return rows[0] || null;
}
/** Ejecuta fn dentro de una transacción (RNF008/RNF009: todo o nada). */
async function tx(fn) {
  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();
    const result = await fn(conn);
    await conn.commit();
    return result;
  } catch (e) {
    await conn.rollback();
    throw e;
  } finally {
    conn.release();
  }
}

module.exports = { pool, q, one, tx };
