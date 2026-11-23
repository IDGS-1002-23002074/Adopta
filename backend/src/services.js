// Servicios compartidos: notificaciones, historial y correo.
const nodemailer = require('nodemailer');
const cfg = require('./config');
const { q } = require('./db');

/** Inserta una notificación (RF043). */
async function notify(conn, idUsuario, titulo, mensaje, tipo, idSolicitud = null) {
  await q(
    'INSERT INTO notificaciones (id_usuario, titulo, mensaje, tipo, id_solicitud) VALUES (?,?,?,?,?)',
    [idUsuario, titulo.slice(0, 120), mensaje, tipo, idSolicitud], conn
  );
}

/** Notifica al usuario dueño de la organización de una mascota. */
async function notifyOrgDeMascota(conn, idMascota, titulo, mensaje, tipo, idSolicitud = null) {
  const rows = await q(
    `SELECT o.id_usuario FROM mascotas m JOIN organizaciones o ON o.id_organizacion=m.id_organizacion
     WHERE m.id_mascota=?`, [idMascota], conn
  );
  if (rows[0]) await notify(conn, rows[0].id_usuario, titulo, mensaje, tipo, idSolicitud);
}

async function historialSolicitud(conn, idSolicitud, idEstado, observacion = null) {
  await q('INSERT INTO historial_solicitud (id_solicitud,id_estado_solicitud,observacion) VALUES (?,?,?)',
    [idSolicitud, idEstado, observacion], conn);
}

let transporter;
async function sendMail(to, subject, html) {
  if (!cfg.smtp.host) {
    console.log(`[correo no configurado] Para: ${to} | ${subject}`);
    return false;
  }
  transporter = transporter || nodemailer.createTransport({
    host: cfg.smtp.host, port: cfg.smtp.port, secure: cfg.smtp.port === 465,
    auth: cfg.smtp.user ? { user: cfg.smtp.user, pass: cfg.smtp.pass } : undefined
  });
  await transporter.sendMail({ from: cfg.smtp.from, to, subject, html });
  return true;
}

module.exports = { notify, notifyOrgDeMascota, historialSolicitud, sendMail };
