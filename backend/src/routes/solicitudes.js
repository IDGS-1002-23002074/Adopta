// Solicitudes de adopción (RF020, RF024-RF026) y documentación del adoptante (módulo 5)
const router = require('express').Router();
const fs = require('fs');
const path = require('path');
const cfg = require('../config');
const { q, one, tx } = require('../db');
const cat = require('../catalogos');
const { notify, notifyOrgDeMascota, historialSolicitud } = require('../services');
const { upload, validar, guardarPrivado, sha256 } = require('../files');
const { HttpError, bad, notFound, conflict, wrap, fileUrl, text, bool, int } = require('../utils');

const ACTIVAS = ['Pendiente', 'En revision', 'Documentacion pendiente'];
const REQ_BASE = ['IDENTIFICACION_FRENTE', 'IDENTIFICACION_REVERSO', 'COMPROBANTE_DOMICILIO', 'FOTOS_HOGAR'];

// ---------------------------------------------------------------- utilidades
async function cargarSolicitud(id, idAdoptante, conn) {
  const s = await one(
    `SELECT s.*,es.nombre AS estado,m.nombre AS mascota,m.id_mascota,o.nombre AS organizacion
     FROM solicitudes_adopcion s
     JOIN estados_solicitud es ON es.id_estado_solicitud=s.id_estado_solicitud
     JOIN mascotas m ON m.id_mascota=s.id_mascota
     JOIN organizaciones o ON o.id_organizacion=m.id_organizacion
     WHERE s.id_solicitud=? AND s.id_adoptante=?`, [id, idAdoptante], conn);
  if (!s) throw notFound('Solicitud no encontrada');
  return s;
}
const editable = (s) => s.estado === 'Documentacion pendiente';

async function requisitosDe(req, idSolicitud, conn) {
  const reqs = await q(
    `SELECT r.id_requisito,r.obligatorio,r.estado,r.motivo_rechazo,r.fecha_revision,
            t.clave,t.nombre,t.descripcion,t.formatos_permitidos,t.tamano_max_mb,t.min_archivos,t.max_archivos,
            t.requiere_fecha_emision,t.vigencia_meses
     FROM requisitos_solicitud r JOIN tipos_documento t ON t.id_tipo_documento=r.id_tipo_documento
     WHERE r.id_solicitud=? ORDER BY r.id_requisito`, [idSolicitud], conn);
  const ids = reqs.map((r) => r.id_requisito);
  const archivos = ids.length ? await q(
    `SELECT id_archivo,id_requisito,nombre_original,extension,mime_type,tamano_bytes,fecha_emision_documento,fecha_carga
     FROM archivos_requisito WHERE id_requisito IN (?) AND es_vigente=TRUE ORDER BY id_archivo`, [ids], conn) : [];
  return reqs.map((r) => {
    const mios = archivos.filter((a) => a.id_requisito === r.id_requisito);
    return {
      ...r,
      obligatorio: !!r.obligatorio, requiere_fecha_emision: !!r.requiere_fecha_emision,
      archivos: mios.map((a) => ({ ...a, url: `${req.protocol}://${req.get('host')}/api/archivos/${a.id_archivo}` })),
      cumple: mios.length >= r.min_archivos
    };
  });
}

async function detalle(req, id, conn) {
  const s = await cargarSolicitud(id, req.user.id_usuario, conn);
  const [historial, requisitos, info, refs, foto] = await Promise.all([
    q(`SELECT h.id_historial,es.nombre AS estado,h.observacion,h.fecha_cambio FROM historial_solicitud h
       JOIN estados_solicitud es ON es.id_estado_solicitud=h.id_estado_solicitud
       WHERE h.id_solicitud=? ORDER BY h.fecha_cambio,h.id_historial`, [id], conn),
    requisitosDe(req, id, conn),
    one('SELECT * FROM informacion_complementaria_solicitud WHERE id_solicitud=?', [id], conn),
    q('SELECT id_referencia,nombre_completo,telefono,relacion,anios_conocidos FROM referencias_personales_solicitud WHERE id_solicitud=? ORDER BY id_referencia', [id], conn),
    one(`SELECT ruta_foto FROM fotos_mascota WHERE id_mascota=? ORDER BY es_principal DESC,id_foto LIMIT 1`, [s.id_mascota], conn)
  ]);
  if (info) { delete info.id_solicitud; }
  const faltantes = requisitos.filter((r) => r.obligatorio && !r.cumple).length;
  const infoOk = !!info && info.estado_formulario === 'Enviado';
  return {
    id_solicitud: s.id_solicitud, fecha_solicitud: s.fecha_solicitud, estado: s.estado,
    id_mascota: s.id_mascota, mascota: s.mascota, foto_mascota: fileUrl(req, foto?.ruta_foto), organizacion: s.organizacion,
    motivo_adopcion: s.motivo_adopcion, experiencia_previa: s.experiencia_previa, condiciones_vivienda: s.condiciones_vivienda,
    observaciones_organizacion: s.observaciones_organizacion, fecha_resolucion: s.fecha_resolucion,
    fecha_limite_documentos: s.fecha_limite_documentos, fecha_envio_documentos: s.fecha_envio_documentos,
    documentacion_completa: !!s.documentacion_completa,
    puede_cancelar: ACTIVAS.includes(s.estado),
    puede_editar_documentos: editable(s),
    puede_enviar: editable(s) && faltantes === 0 && infoOk && refs.length >= 2,
    pendientes: { documentos: faltantes, informacion: !infoOk, referencias: Math.max(0, 2 - refs.length) },
    historial, requisitos, informacion: info, referencias: refs
  };
}

async function asegurarRequisito(conn, idSolicitud, clave, obligatorio) {
  const idTipo = await cat.id('tipoDocumento', clave);
  await q(
    `INSERT INTO requisitos_solicitud (id_solicitud,id_tipo_documento,obligatorio) VALUES (?,?,?)
     ON DUPLICATE KEY UPDATE obligatorio=VALUES(obligatorio)`, [idSolicitud, idTipo, obligatorio], conn);
}
async function quitarRequisitoSinArchivos(conn, idSolicitud, clave) {
  const idTipo = await cat.id('tipoDocumento', clave);
  await q(
    `DELETE r FROM requisitos_solicitud r WHERE r.id_solicitud=? AND r.id_tipo_documento=?
     AND NOT EXISTS (SELECT 1 FROM archivos_requisito a WHERE a.id_requisito=r.id_requisito)`, [idSolicitud, idTipo], conn);
}

// ---------------------------------------------------------------- listado y alta
// RF024/RF025 — Mis solicitudes y su estado
router.get('/', wrap(async (req, res) => {
  const rows = await q(
    `SELECT s.id_solicitud,s.fecha_solicitud,s.fecha_limite_documentos,s.documentacion_completa,es.nombre AS estado,
            m.id_mascota,m.nombre AS mascota,m.especie,o.nombre AS organizacion,
            (SELECT fm.ruta_foto FROM fotos_mascota fm WHERE fm.id_mascota=m.id_mascota ORDER BY fm.es_principal DESC,fm.id_foto LIMIT 1) AS foto
     FROM solicitudes_adopcion s
     JOIN estados_solicitud es ON es.id_estado_solicitud=s.id_estado_solicitud
     JOIN mascotas m ON m.id_mascota=s.id_mascota
     JOIN organizaciones o ON o.id_organizacion=m.id_organizacion
     WHERE s.id_adoptante=? ORDER BY s.fecha_solicitud DESC, s.id_solicitud DESC`, [req.user.id_usuario]);
  res.json({
    items: rows.map((r) => ({
      id_solicitud: r.id_solicitud, fecha_solicitud: r.fecha_solicitud, estado: r.estado, id_mascota: r.id_mascota,
      mascota: r.mascota, especie: r.especie, organizacion: r.organizacion, foto_mascota: fileUrl(req, r.foto),
      fecha_limite_documentos: r.fecha_limite_documentos, documentacion_completa: !!r.documentacion_completa,
      requiere_documentos: r.estado === 'Documentacion pendiente', puede_cancelar: ACTIVAS.includes(r.estado)
    })),
    activas: rows.filter((r) => ACTIVAS.includes(r.estado)).length,
    maximo_activas: cfg.maxSolicitudesActivas
  });
}));

// RF020 — Registrar solicitud (máx. 3 activas, compromiso explícito)
router.post('/', wrap(async (req, res) => {
  const b = req.body || {};
  const idMascota = int(b.id_mascota);
  const motivo = text(b.motivo_adopcion, 2000), exp = text(b.experiencia_previa, 2000), viv = text(b.condiciones_vivienda, 2000);
  const errores = {};
  if (!idMascota) errores.id_mascota = 'Selecciona una mascota';
  if (!motivo || motivo.length < 10) errores.motivo_adopcion = 'Cuéntanos por qué quieres adoptar (mínimo 10 caracteres)';
  if (!viv || viv.length < 10) errores.condiciones_vivienda = 'Describe brevemente tu vivienda (mínimo 10 caracteres)';
  if (b.acepta_compromisos !== true) errores.acepta_compromisos = 'Debes aceptar los compromisos de adopción responsable';
  if (Object.keys(errores).length) throw bad('Revisa los datos de la solicitud', errores);

  const idNueva = await tx(async (conn) => {
    const m = await one(
      `SELECT m.id_mascota,m.nombre,em.nombre AS estado FROM mascotas m
       JOIN estados_mascota em ON em.id_estado_mascota=m.id_estado_mascota WHERE m.id_mascota=? FOR UPDATE`, [idMascota], conn);
    if (!m) throw notFound('Mascota no encontrada');
    if (m.estado !== 'Disponible') throw conflict(`${m.nombre} ya no está disponible para adopción`);

    const activas = await q(
      `SELECT s.id_solicitud,s.id_mascota FROM solicitudes_adopcion s
       JOIN estados_solicitud es ON es.id_estado_solicitud=s.id_estado_solicitud
       WHERE s.id_adoptante=? AND es.nombre IN (?) FOR UPDATE`, [req.user.id_usuario, ACTIVAS], conn);
    if (activas.some((a) => a.id_mascota === idMascota)) throw conflict(`Ya tienes una solicitud activa para ${m.nombre}`);
    if (activas.length >= cfg.maxSolicitudesActivas) {
      throw conflict(`Tienes ${cfg.maxSolicitudesActivas} solicitudes activas. Espera una respuesta o cancela una para enviar otra.`);
    }

    const auto = cfg.autoSolicitarDocumentos;
    const estado = await cat.id('estadoSolicitud', auto ? 'Documentacion pendiente' : 'Pendiente');
    const r = await q(
      `INSERT INTO solicitudes_adopcion
       (id_mascota,id_adoptante,motivo_adopcion,experiencia_previa,condiciones_vivienda,id_estado_solicitud,fecha_limite_documentos)
       VALUES (?,?,?,?,?,?,${auto ? 'DATE_ADD(CURDATE(), INTERVAL ? DAY)' : 'NULL'})`,
      auto ? [idMascota, req.user.id_usuario, motivo, exp, viv, estado, cfg.diasLimiteDocumentos]
           : [idMascota, req.user.id_usuario, motivo, exp, viv, estado], conn);
    const id = r.insertId;
    await historialSolicitud(conn, id, await cat.id('estadoSolicitud', 'Pendiente'), 'Solicitud enviada por el adoptante');
    if (auto) {
      for (const clave of REQ_BASE) await asegurarRequisito(conn, id, clave, true);
      await historialSolicitud(conn, id, estado, 'Se solicitó la documentación para continuar');
      await notify(conn, req.user.id_usuario, 'Sube tus documentos',
        `Para continuar con tu solicitud de ${m.nombre} necesitamos tu documentación. Tienes ${cfg.diasLimiteDocumentos} días.`, 'Documentos', id);
    }
    await notify(conn, req.user.id_usuario, 'Solicitud enviada', `Recibimos tu solicitud para adoptar a ${m.nombre}.`, 'Solicitudes', id);
    await notifyOrgDeMascota(conn, idMascota, 'Nueva solicitud de adopción',
      `${req.user.nombre} ${req.user.apellido_paterno} solicitó adoptar a ${m.nombre}.`, 'Solicitudes', id);
    return id;
  });
  res.status(201).json(await detalle(req, idNueva));
}));

router.get('/:id', wrap(async (req, res) => {
  const id = int(req.params.id);
  if (!id) throw notFound('Solicitud no encontrada');
  res.json(await detalle(req, id));
}));

// RF026 — Cancelar
router.post('/:id/cancelar', wrap(async (req, res) => {
  const id = int(req.params.id);
  await tx(async (conn) => {
    const s = await cargarSolicitud(id, req.user.id_usuario, conn);
    if (!ACTIVAS.includes(s.estado)) throw conflict(`No puedes cancelar una solicitud en estado "${s.estado}"`);
    const cancelada = await cat.id('estadoSolicitud', 'Cancelada');
    await q('UPDATE solicitudes_adopcion SET id_estado_solicitud=?,fecha_resolucion=NOW() WHERE id_solicitud=?', [cancelada, id], conn);
    await historialSolicitud(conn, id, cancelada, text(req.body?.motivo, 500) || 'Cancelada por el adoptante');
    await notify(conn, req.user.id_usuario, 'Solicitud cancelada', `Cancelaste tu solicitud para ${s.mascota}.`, 'Solicitudes', id);
    await notifyOrgDeMascota(conn, s.id_mascota, 'Solicitud cancelada',
      `${req.user.nombre} canceló su solicitud para ${s.mascota}.`, 'Solicitudes', id);
  });
  res.json(await detalle(req, id));
}));

// ---------------------------------------------------------------- información del hogar
const ENUMS = {
  regimen_vivienda: ['Propia', 'Rentada', 'Familiar', 'Otro'],
  horas_solo_dia: ['Menos de 4 horas', 'De 4 a 8 horas', 'Mas de 8 horas'],
  lugar_dormir: ['Dentro de casa', 'Patio o exterior', 'Area techada exterior', 'Otro'],
  presupuesto_mensual: ['Menos de $500', '$500 a $1,000', '$1,001 a $2,000', 'Mas de $2,000']
};
const BOOLS = ['arrendador_permite_mascotas', 'tiene_espacio_exterior', 'espacio_exterior_cercado', 'hay_menores', 'hogar_de_acuerdo',
  'tiene_otras_mascotas', 'otras_esterilizadas', 'otras_vacunadas', 'acepta_visita_domiciliaria', 'acepta_seguimiento',
  'compromiso_esterilizacion', 'acepta_aviso_privacidad', 'declaracion_veracidad'];
const NOT_NULL = new Set(['tiene_espacio_exterior', 'hay_menores', 'hogar_de_acuerdo', 'tiene_otras_mascotas',
  'acepta_visita_domiciliaria', 'acepta_seguimiento', 'acepta_aviso_privacidad', 'declaracion_veracidad']);
const DEFAULTS = {
  regimen_vivienda: 'Propia', tiene_espacio_exterior: false, num_personas_hogar: 1, hay_menores: false, hogar_de_acuerdo: false,
  horas_solo_dia: 'De 4 a 8 horas', lugar_dormir: 'Dentro de casa', presupuesto_mensual: 'Menos de $500', tiene_otras_mascotas: false,
  acepta_visita_domiciliaria: false, acepta_seguimiento: false, acepta_aviso_privacidad: false, declaracion_veracidad: false
};

router.put('/:id/informacion', wrap(async (req, res) => {
  const id = int(req.params.id);
  const b = req.body || {};
  const enviado = b.estado_formulario === 'Enviado';
  await tx(async (conn) => {
    const s = await cargarSolicitud(id, req.user.id_usuario, conn);
    if (!editable(s)) throw conflict('Esta solicitud ya no admite cambios en la información');

    const d = { ...DEFAULTS };
    const prev = await one('SELECT * FROM informacion_complementaria_solicitud WHERE id_solicitud=?', [id], conn);
    if (prev) for (const k of Object.keys(prev)) if (prev[k] !== null) d[k] = prev[k];
    for (const k of Object.keys(ENUMS)) {
      if (b[k] !== undefined && b[k] !== null) {
        if (!ENUMS[k].includes(b[k])) throw bad('Revisa el formulario', { [k]: 'Valor no válido' });
        d[k] = b[k];
      }
    }
    // Las columnas NOT NULL conservan su valor anterior (o el valor por defecto) si llegan en null.
    for (const k of BOOLS) {
      if (b[k] === undefined || (b[k] === null && NOT_NULL.has(k))) continue;
      d[k] = b[k] === null ? null : bool(b[k]);
    }
    for (const [k, max] of [['num_personas_hogar', 30], ['superficie_m2', 5000], ['cantidad_otras_mascotas', 50], ['anios_conocidos', 99]]) {
      if (b[k] !== undefined) {
        if (b[k] === null && k === 'num_personas_hogar') continue;
        const n = b[k] === null ? null : int(b[k]);
        if (b[k] !== null && (n === null || n < 0 || n > max)) throw bad('Revisa el formulario', { [k]: 'Número no válido' });
        d[k] = n;
      }
    }
    for (const [k, max] of [['cuidador_alterno', 120], ['especies_otras_mascotas', 120], ['historial_mascotas_previas', 2000],
      ['veterinario_nombre', 120], ['veterinario_telefono', 20], ['firma_nombre', 160]]) {
      if (b[k] !== undefined) d[k] = text(b[k], max);
    }

    if (enviado) {
      const e = {};
      if (!d.num_personas_hogar || d.num_personas_hogar < 1) e.num_personas_hogar = 'Indica cuántas personas viven en el hogar';
      if (d.regimen_vivienda === 'Rentada' && d.arrendador_permite_mascotas !== true) e.arrendador_permite_mascotas = 'Necesitas autorización de tu arrendador';
      if (d.tiene_espacio_exterior && d.espacio_exterior_cercado === null) e.espacio_exterior_cercado = 'Indica si está cercado';
      if (d.tiene_otras_mascotas) {
        if (!d.cantidad_otras_mascotas) e.cantidad_otras_mascotas = 'Indica cuántas mascotas tienes';
        if (!d.especies_otras_mascotas) e.especies_otras_mascotas = 'Indica de qué especies';
        if (d.otras_esterilizadas === null) e.otras_esterilizadas = 'Indica si están esterilizadas';
        if (d.otras_vacunadas === null) e.otras_vacunadas = 'Indica si están vacunadas';
      }
      if (!d.hogar_de_acuerdo) e.hogar_de_acuerdo = 'Todos los integrantes del hogar deben estar de acuerdo';
      if (!d.acepta_visita_domiciliaria) e.acepta_visita_domiciliaria = 'Debes aceptar la visita domiciliaria';
      if (!d.acepta_seguimiento) e.acepta_seguimiento = 'Debes aceptar el seguimiento posterior';
      if (!d.acepta_aviso_privacidad) e.acepta_aviso_privacidad = 'Debes aceptar el aviso de privacidad';
      if (!d.declaracion_veracidad) e.declaracion_veracidad = 'Debes declarar que la información es verdadera';
      if (!d.firma_nombre || d.firma_nombre.length < 5) e.firma_nombre = 'Escribe tu nombre completo como firma';
      if (Object.keys(e).length) throw bad('Completa los campos marcados', e);
    }

    const cols = ['regimen_vivienda', 'arrendador_permite_mascotas', 'tiene_espacio_exterior', 'espacio_exterior_cercado', 'superficie_m2',
      'num_personas_hogar', 'hay_menores', 'hogar_de_acuerdo', 'horas_solo_dia', 'lugar_dormir', 'cuidador_alterno', 'presupuesto_mensual',
      'tiene_otras_mascotas', 'cantidad_otras_mascotas', 'especies_otras_mascotas', 'otras_esterilizadas', 'otras_vacunadas',
      'historial_mascotas_previas', 'veterinario_nombre', 'veterinario_telefono', 'acepta_visita_domiciliaria', 'acepta_seguimiento',
      'compromiso_esterilizacion', 'acepta_aviso_privacidad', 'declaracion_veracidad', 'firma_nombre'];
    const vals = cols.map((c) => (d[c] === undefined ? null : d[c]));
    const extra = enviado ? ['Enviado', new Date()] : ['Borrador', null];
    if (prev) {
      await q(`UPDATE informacion_complementaria_solicitud SET ${cols.map((c) => `${c}=?`).join(',')},estado_formulario=?,fecha_firma=? WHERE id_solicitud=?`,
        [...vals, ...extra, id], conn);
    } else {
      await q(`INSERT INTO informacion_complementaria_solicitud (id_solicitud,${cols.join(',')},estado_formulario,fecha_firma) VALUES (?,${cols.map(() => '?').join(',')},?,?)`,
        [id, ...vals, ...extra], conn);
    }

    // Requisitos condicionales según las respuestas
    if (d.regimen_vivienda === 'Rentada') await asegurarRequisito(conn, id, 'CARTA_ARRENDADOR', true);
    else await quitarRequisitoSinArchivos(conn, id, 'CARTA_ARRENDADOR');
    if (d.tiene_otras_mascotas && d.otras_vacunadas) await asegurarRequisito(conn, id, 'CARTILLA_VACUNACION', true);
    else await quitarRequisitoSinArchivos(conn, id, 'CARTILLA_VACUNACION');
    if (d.tiene_otras_mascotas) await asegurarRequisito(conn, id, 'COMPROBANTE_ESTERILIZACION', false);
    else await quitarRequisitoSinArchivos(conn, id, 'COMPROBANTE_ESTERILIZACION');
  });
  res.json(await detalle(req, id));
}));

// ---------------------------------------------------------------- referencias personales
router.post('/:id/referencias', wrap(async (req, res) => {
  const id = int(req.params.id);
  const b = req.body || {};
  const nombre = text(b.nombre_completo, 120), tel = text(b.telefono, 20), rel = text(b.relacion, 60);
  const anios = b.anios_conocidos === undefined || b.anios_conocidos === null || b.anios_conocidos === '' ? null : int(b.anios_conocidos);
  const e = {};
  if (!nombre || nombre.length < 5) e.nombre_completo = 'Escribe el nombre completo';
  if (!tel || !/^[0-9+\s()-]{7,20}$/.test(tel)) e.telefono = 'Teléfono no válido';
  if (!rel) e.relacion = 'Indica la relación (familiar, amigo...)';
  if (anios !== null && (anios < 0 || anios > 99)) e.anios_conocidos = 'Número no válido';
  if (Object.keys(e).length) throw bad('Revisa los datos de la referencia', e);
  await tx(async (conn) => {
    const s = await cargarSolicitud(id, req.user.id_usuario, conn);
    if (!editable(s)) throw conflict('Esta solicitud ya no admite cambios');
    const [{ n }] = await q('SELECT COUNT(*) AS n FROM referencias_personales_solicitud WHERE id_solicitud=?', [id], conn);
    if (n >= 5) throw conflict('Máximo 5 referencias');
    await q('INSERT INTO referencias_personales_solicitud (id_solicitud,nombre_completo,telefono,relacion,anios_conocidos) VALUES (?,?,?,?,?)',
      [id, nombre, tel, rel, anios], conn);
  });
  res.status(201).json(await detalle(req, id));
}));
router.delete('/:id/referencias/:idRef', wrap(async (req, res) => {
  const id = int(req.params.id);
  await tx(async (conn) => {
    const s = await cargarSolicitud(id, req.user.id_usuario, conn);
    if (!editable(s)) throw conflict('Esta solicitud ya no admite cambios');
    await q('DELETE FROM referencias_personales_solicitud WHERE id_referencia=? AND id_solicitud=?', [int(req.params.idRef), id], conn);
  });
  res.json(await detalle(req, id));
}));

// ---------------------------------------------------------------- archivos
router.post('/:id/requisitos/:idReq/archivos', upload.single('archivo'), wrap(async (req, res) => {
  const id = int(req.params.id), idReq = int(req.params.idReq);
  await tx(async (conn) => {
    const s = await cargarSolicitud(id, req.user.id_usuario, conn);
    if (!editable(s)) throw conflict('Esta solicitud ya no admite documentos');
    const r = await one(
      `SELECT r.id_requisito,r.estado,t.nombre,t.formatos_permitidos,t.tamano_max_mb,t.min_archivos,t.max_archivos,t.requiere_fecha_emision,t.vigencia_meses
       FROM requisitos_solicitud r JOIN tipos_documento t ON t.id_tipo_documento=r.id_tipo_documento
       WHERE r.id_requisito=? AND r.id_solicitud=? FOR UPDATE`, [idReq, id], conn);
    if (!r) throw notFound('Requisito no encontrado');
    if (r.estado === 'Aprobado') throw conflict('Este documento ya fue aprobado');

    const kind = validar(req.file, r.formatos_permitidos.split(',').map((x) => x.trim()), r.tamano_max_mb);
    let fechaEmision = null;
    if (r.requiere_fecha_emision) {
      fechaEmision = text(req.body?.fecha_emision_documento, 10);
      if (!fechaEmision || !/^\d{4}-\d{2}-\d{2}$/.test(fechaEmision)) throw bad('Indica la fecha de emisión del documento', { fecha_emision_documento: 'Requerida (AAAA-MM-DD)' });
      const f = new Date(fechaEmision + 'T00:00:00');
      const hoy = new Date();
      if (f > hoy) throw bad('La fecha de emisión no puede ser futura', { fecha_emision_documento: 'Fecha futura' });
      if (r.vigencia_meses) {
        const limite = new Date(hoy); limite.setMonth(limite.getMonth() - r.vigencia_meses);
        if (f < limite) throw bad(`El documento debe tener máximo ${r.vigencia_meses} meses de antigüedad`, { fecha_emision_documento: 'Documento vencido' });
      }
    }

    const hash = sha256(req.file.buffer);
    const vigentes = await q('SELECT id_archivo,hash_sha256 FROM archivos_requisito WHERE id_requisito=? AND es_vigente=TRUE ORDER BY id_archivo', [idReq], conn);
    if (vigentes.some((v) => v.hash_sha256 === hash)) throw conflict('Ya cargaste este mismo archivo');
    if (r.max_archivos === 1) {
      await q('UPDATE archivos_requisito SET es_vigente=FALSE WHERE id_requisito=?', [idReq], conn); // reemplazo con historial
    } else if (vigentes.length >= r.max_archivos) {
      throw conflict(`Máximo ${r.max_archivos} archivos. Elimina uno para agregar otro.`);
    }

    const { nombre, ruta } = guardarPrivado(`solicitudes/${id}`, req.file.buffer, kind.ext);
    const original = path.basename(req.file.originalname || `archivo.${kind.ext}`).replace(/[^\w.\- ()áéíóúñÁÉÍÓÚÑ]/g, '_').slice(0, 200);
    await q(
      `INSERT INTO archivos_requisito (id_requisito,id_usuario_carga,nombre_original,nombre_almacenado,ruta_relativa,mime_type,extension,tamano_bytes,hash_sha256,fecha_emision_documento,origen)
       VALUES (?,?,?,?,?,?,?,?,?,?,'Movil')`,
      [idReq, req.user.id_usuario, original, nombre, ruta, kind.mime, kind.ext, req.file.size, hash, fechaEmision], conn);
    const [{ n }] = await q('SELECT COUNT(*) AS n FROM archivos_requisito WHERE id_requisito=? AND es_vigente=TRUE', [idReq], conn);
    if (n >= r.min_archivos) {
      await q(`UPDATE requisitos_solicitud SET estado='Cargado',motivo_rechazo=NULL,id_usuario_revisor=NULL,fecha_revision=NULL WHERE id_requisito=?`, [idReq], conn);
    }
  });
  res.status(201).json(await detalle(req, id));
}));

router.delete('/:id/archivos/:idArchivo', wrap(async (req, res) => {
  const id = int(req.params.id);
  await tx(async (conn) => {
    const s = await cargarSolicitud(id, req.user.id_usuario, conn);
    if (!editable(s)) throw conflict('Esta solicitud ya no admite cambios');
    const a = await one(
      `SELECT a.id_archivo,r.id_requisito,r.estado,t.min_archivos FROM archivos_requisito a
       JOIN requisitos_solicitud r ON r.id_requisito=a.id_requisito
       JOIN tipos_documento t ON t.id_tipo_documento=r.id_tipo_documento
       WHERE a.id_archivo=? AND r.id_solicitud=? AND a.es_vigente=TRUE`, [int(req.params.idArchivo), id], conn);
    if (!a) throw notFound('Archivo no encontrado');
    if (a.estado === 'Aprobado') throw conflict('Este documento ya fue aprobado');
    await q('UPDATE archivos_requisito SET es_vigente=FALSE WHERE id_archivo=?', [a.id_archivo], conn);
    const [{ n }] = await q('SELECT COUNT(*) AS n FROM archivos_requisito WHERE id_requisito=? AND es_vigente=TRUE', [a.id_requisito], conn);
    if (n < a.min_archivos) await q(`UPDATE requisitos_solicitud SET estado='Pendiente' WHERE id_requisito=?`, [a.id_requisito], conn);
  });
  res.json(await detalle(req, id));
}));

// Descarga protegida: solo el dueño de la solicitud (los archivos no son públicos)
const archivos = require('express').Router();
archivos.get('/:idArchivo', wrap(async (req, res) => {
  const a = await one(
    `SELECT a.ruta_relativa,a.mime_type,a.nombre_original FROM archivos_requisito a
     JOIN requisitos_solicitud r ON r.id_requisito=a.id_requisito
     JOIN solicitudes_adopcion s ON s.id_solicitud=r.id_solicitud
     WHERE a.id_archivo=? AND s.id_adoptante=?`, [int(req.params.idArchivo), req.user.id_usuario]);
  if (!a) throw notFound('Archivo no encontrado');
  const full = path.join(cfg.privateDir, a.ruta_relativa);
  if (!full.startsWith(cfg.privateDir) || !fs.existsSync(full)) throw notFound('Archivo no encontrado');
  res.setHeader('Content-Type', a.mime_type);
  res.setHeader('Content-Disposition', `inline; filename*=UTF-8''${encodeURIComponent(a.nombre_original)}`);
  res.setHeader('Cache-Control', 'private, max-age=300');
  fs.createReadStream(full).pipe(res);
}));

// ---------------------------------------------------------------- enviar documentación a revisión
router.post('/:id/enviar', wrap(async (req, res) => {
  const id = int(req.params.id);
  await tx(async (conn) => {
    const s = await cargarSolicitud(id, req.user.id_usuario, conn);
    if (!editable(s)) throw conflict('Esta solicitud no está esperando documentación');
    const reqs = await requisitosDe(req, id, conn);
    const falta = reqs.filter((r) => r.obligatorio && !r.cumple).map((r) => r.nombre);
    const rechazados = reqs.filter((r) => r.estado === 'Rechazado').map((r) => r.nombre);
    const info = await one('SELECT estado_formulario FROM informacion_complementaria_solicitud WHERE id_solicitud=?', [id], conn);
    const [{ n }] = await q('SELECT COUNT(*) AS n FROM referencias_personales_solicitud WHERE id_solicitud=?', [id], conn);
    const e = {};
    if (falta.length) e.documentos = `Falta: ${falta.join(', ')}`;
    else if (rechazados.length) e.documentos = `Vuelve a cargar: ${rechazados.join(', ')}`;
    if (!info || info.estado_formulario !== 'Enviado') e.informacion = 'Completa la información del hogar';
    if (n < 2) e.referencias = 'Agrega al menos 2 referencias personales';
    if (Object.keys(e).length) throw bad('Aún no puedes enviar tu documentación', e);

    const revision = await cat.id('estadoSolicitud', 'En revision');
    await q('UPDATE solicitudes_adopcion SET id_estado_solicitud=?,fecha_envio_documentos=NOW() WHERE id_solicitud=?', [revision, id], conn);
    await historialSolicitud(conn, id, revision, 'Documentación enviada por el adoptante');
    await notify(conn, req.user.id_usuario, 'Documentación enviada', `${s.organizacion} revisará tu documentación de ${s.mascota}.`, 'Documentos', id);
    await notifyOrgDeMascota(conn, s.id_mascota, 'Documentación recibida',
      `${req.user.nombre} envió su documentación para adoptar a ${s.mascota}.`, 'Documentos', id);
  });
  res.json(await detalle(req, id));
}));

module.exports = { router, archivos };
