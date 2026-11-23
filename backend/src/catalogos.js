// Cache de catálogos (id <-> nombre). Los estados cambian muy poco.
const { q } = require('./db');
const { HttpError } = require('./utils');

const cache = {};
async function load(key, sql) {
  if (!cache[key]) {
    const rows = await q(sql);
    cache[key] = { byName: new Map(rows.map((r) => [r.nombre, r.id])), byId: new Map(rows.map((r) => [r.id, r.nombre])) };
  }
  return cache[key];
}
const defs = {
  rol: 'SELECT id_rol AS id, nombre FROM roles',
  estadoSolicitud: 'SELECT id_estado_solicitud AS id, nombre FROM estados_solicitud',
  estadoMascota: 'SELECT id_estado_mascota AS id, nombre FROM estados_mascota',
  estadoAdaptacion: 'SELECT id_estado_adaptacion AS id, nombre FROM estados_adaptacion',
  tipoDocumento: 'SELECT id_tipo_documento AS id, clave AS nombre FROM tipos_documento'
};
async function id(kind, nombre) {
  const c = await load(kind, defs[kind]);
  const v = c.byName.get(nombre);
  if (v === undefined) throw new HttpError(500, `Catálogo incompleto: ${kind} "${nombre}". ¿Cargaste Adopta_DB_completa.sql?`);
  return v;
}
async function name(kind, idValue) {
  const c = await load(kind, defs[kind]);
  return c.byId.get(idValue);
}
module.exports = { id, name };
