// Prueba de humo de punta a punta contra la BD real. Uso: npm run db:reset && npm run smoke
const app = require('../src/server');
const { pool } = require('../src/db');

let base, fails = 0, total = 0;
const ok = (c, msg) => { total++; if (!c) { fails++; console.log('  ✗', msg); } else console.log('  ✓', msg); };

async function api(method, path, { token, json, form } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  let body;
  if (json) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(json); }
  if (form) body = form;
  const r = await fetch(base + path, { method, headers, body });
  let data = null; try { data = await r.json(); } catch (e) { /* sin cuerpo */ }
  return { s: r.status, d: data };
}
const jpg = (n) => Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0, 16]), Buffer.from(`JFIF-${n}-`.padEnd(40, 'x'))]);
const pdf = Buffer.from('%PDF-1.4 contenido de prueba');
function archivo(buf, name, mime, extra = {}) {
  const f = new FormData();
  f.append('archivo', new Blob([buf], { type: mime }), name);
  for (const [k, v] of Object.entries(extra)) f.append(k, v);
  return f;
}
const login = async (c, p = 'Adopta123') => (await api('POST', '/api/auth/login', { json: { correo: c, contrasena: p } })).d?.token;

(async () => {
  const server = app.listen(0); base = `http://127.0.0.1:${server.address().port}`;
  const correo = `carlos${Date.now()}@test.com`;

  console.log('\nAcceso (RF001-RF003, RF006)');
  ok((await api('GET', '/api/health')).d.ok, 'health: BD conectada');
  ok((await api('POST', '/api/auth/register', { json: { nombre: 'C', correo: 'x', contrasena: '123' } })).s === 422, 'registro con datos inválidos -> 422');
  let r = await api('POST', '/api/auth/register', { json: { nombre: 'Carlos', apellido_paterno: 'Ruiz', correo, contrasena: 'Clave1234', telefono: '4771234567' } });
  ok(r.s === 201 && r.d.token && r.d.usuario.rol === 'Adoptante', 'registro de adoptante -> 201 con token y rol');
  const tk = r.d.token;
  ok((await api('POST', '/api/auth/register', { json: { nombre: 'Carlos', apellido_paterno: 'Ruiz', correo, contrasena: 'Clave1234' } })).s === 409, 'correo duplicado -> 409');
  ok((await api('POST', '/api/auth/login', { json: { correo, contrasena: 'mala' } })).s === 401, 'login incorrecto -> 401');
  ok((await api('GET', '/api/me')).s === 401, 'sin token -> 401');
  r = await api('POST', '/api/auth/forgot', { json: { correo } });
  ok(/^\d{6}$/.test(r.d.codigo_dev), 'forgot genera código de 6 dígitos');
  ok((await api('POST', '/api/auth/reset', { json: { correo, codigo: '000000', nueva_contrasena: 'Nueva1234' } })).s === 400, 'reset con código malo -> 400');
  ok((await api('POST', '/api/auth/reset', { json: { correo, codigo: r.d.codigo_dev, nueva_contrasena: 'Nueva1234' } })).s === 200, 'reset con código válido');
  ok((await api('POST', '/api/auth/reset', { json: { correo, codigo: r.d.codigo_dev, nueva_contrasena: 'Otra12345' } })).s === 400, 'el código no se puede reutilizar');
  const t = await login(correo, 'Nueva1234');
  ok(!!t, 'login con la nueva contraseña');
  ok((await api('GET', '/api/mascotas', { token: await login('patitas@adopta.com') })).s === 403, 'una organización no accede a la API móvil -> 403');

  console.log('\nPerfil y preferencias (RF004, RF005)');
  r = await api('PUT', '/api/me', { token: t, json: { usuario: { telefono: '4779998888' }, perfil: { ciudad: 'León', tiene_mascotas: true, ocupacion: 'Docente' } } });
  ok(r.s === 200 && r.d.perfil.ciudad === 'León' && r.d.perfil.tiene_mascotas === true && r.d.usuario.telefono === '4779998888', 'actualizar perfil');
  ok((await api('PUT', '/api/me', { token: t, json: { usuario: { nombre: '' } } })).s === 422, 'validación de nombre vacío -> 422');
  r = await api('PUT', '/api/me/preferencias', { token: t, json: { especie: 'Perro', tamano: 'Mediano', edad_preferida: '1 a 3 años' } });
  ok(r.s === 200 && (await api('GET', '/api/me/preferencias', { token: t })).d.especie === 'Perro', 'guardar preferencias');
  const f = new FormData(); f.append('foto', new Blob([jpg(1)], { type: 'image/jpeg' }), 'yo.jpg');
  ok((await api('POST', '/api/me/foto', { token: t, form: f })).s === 200, 'subir foto de perfil');
  const bad = new FormData(); bad.append('foto', new Blob(['no soy imagen, solo texto largo'], { type: 'image/jpeg' }), 'x.jpg');
  ok((await api('POST', '/api/me/foto', { token: t, form: bad })).s === 422, 'rechaza archivo que no es imagen (aunque diga image/jpeg)');

  console.log('\nCatálogo y favoritos (RF015-RF017, RF019)');
  r = await api('GET', '/api/mascotas', { token: t });
  ok(r.s === 200 && r.d.total === 9 && r.d.items.length === 9, `catálogo: 9 disponibles (Toby, adoptado, no aparece) -> total=${r.d.total}`);
  ok(r.d.items.find((m) => m.nombre === 'Max').foto_principal.includes('/uploads/mascotas/pet_max.jpg'), 'foto principal con URL absoluta');
  ok((await api('GET', '/api/mascotas?especie=Gato', { token: t })).d.total === 3, 'filtro por especie');
  ok((await api('GET', '/api/mascotas?tamano=Grande', { token: t })).d.total === 3, 'filtro por tamaño');
  ok((await api('GET', '/api/mascotas?edad=0', { token: t })).d.items.map((m) => m.nombre).join() === 'Pelusa', 'filtro edad < 1 año');
  ok((await api('GET', '/api/mascotas?edad=2', { token: t })).d.items.map((m) => m.nombre).join() === 'Coco', 'filtro edad >= 4 años');
  ok((await api('GET', '/api/mascotas?sexo=Macho&ciudad=Guanajuato', { token: t })).d.total === 2, 'filtros sexo + ubicación');
  ok((await api('GET', '/api/mascotas?q=beag', { token: t })).d.total === 1, 'búsqueda por texto (raza)');
  ok((await api('GET', '/api/mascotas?limit=4&offset=8', { token: t })).d.items.length === 1, 'paginación');
  const max = (await api('GET', '/api/mascotas?q=Max', { token: t })).d.items[0].id_mascota;
  r = await api('GET', `/api/mascotas/${max}`, { token: t });
  ok(r.s === 200 && r.d.organizacion_detalle.nombre === 'Refugio Patitas Felices' && r.d.fotos.length === 1 && r.d.mi_solicitud === null, 'detalle con organización y fotos');
  ok((await api('POST', `/api/favoritos/${max}`, { token: t })).s === 201, 'agregar a favoritos');
  await api('POST', `/api/favoritos/${max}`, { token: t });
  r = await api('GET', '/api/favoritos', { token: t });
  ok(r.d.total === 1 && r.d.items[0].es_favorito, 'favoritos idempotente y listado');
  await api('DELETE', `/api/favoritos/${max}`, { token: t });
  ok((await api('GET', '/api/favoritos', { token: t })).d.total === 0, 'quitar de favoritos');
  ok(!!(await api('GET', '/api/catalogos', { token: t })).d.motivos_devolucion.length, 'catálogos (motivos, tipos de documento)');

  console.log('\nSolicitud de adopción y documentación (RF020, RF024-RF026)');
  ok((await api('POST', '/api/solicitudes', { token: t, json: { id_mascota: max, motivo_adopcion: 'corto', condiciones_vivienda: 'x' } })).s === 422, 'solicitud sin compromiso ni datos -> 422');
  const body = (id) => ({ id_mascota: id, motivo_adopcion: 'Quiero darle un hogar lleno de cariño', experiencia_previa: 'Tuve perros', condiciones_vivienda: 'Casa con patio cercado', acepta_compromisos: true });
  r = await api('POST', '/api/solicitudes', { token: t, json: body(max) });
  ok(r.s === 201 && r.d.estado === 'Documentacion pendiente' && r.d.requisitos.length === 4 && r.d.historial.length === 2, 'crear solicitud -> Documentacion pendiente con checklist e historial');
  const sid = r.d.id_solicitud;
  ok((await api('POST', '/api/solicitudes', { token: t, json: body(max) })).s === 409, 'solicitud duplicada para la misma mascota -> 409');
  const ids = (await api('GET', '/api/mascotas', { token: t })).d.items.map((m) => m.id_mascota).filter((i) => i !== max);
  ok((await api('POST', '/api/solicitudes', { token: t, json: body(ids[0]) })).s === 201, '2.ª solicitud');
  ok((await api('POST', '/api/solicitudes', { token: t, json: body(ids[1]) })).s === 201, '3.ª solicitud');
  r = await api('POST', '/api/solicitudes', { token: t, json: body(ids[2]) });
  ok(r.s === 409 && /3 solicitudes activas/.test(r.d.error), 'la 4.ª excede el máximo de 3 activas -> 409');
  r = await api('GET', '/api/solicitudes', { token: t });
  ok(r.d.activas === 3 && r.d.items.length === 3, 'listado de solicitudes con contador de activas');
  const cancel = r.d.items.find((s) => s.id_mascota === ids[1]).id_solicitud;
  r = await api('POST', `/api/solicitudes/${cancel}/cancelar`, { token: t, json: {} });
  ok(r.d.estado === 'Cancelada' && r.d.historial.at(-1).estado === 'Cancelada', 'cancelar solicitud (RF026)');
  ok((await api('POST', `/api/solicitudes/${cancel}/cancelar`, { token: t, json: {} })).s === 409, 'no se cancela dos veces');

  r = await api('GET', `/api/solicitudes/${sid}`, { token: t });
  const rq = Object.fromEntries(r.d.requisitos.map((x) => [x.clave, x.id_requisito]));
  const up = (clave, buf, name, mime, extra) => api('POST', `/api/solicitudes/${sid}/requisitos/${rq[clave]}/archivos`, { token: t, form: archivo(buf, name, mime, extra) });
  ok((await api('POST', `/api/solicitudes/${sid}/enviar`, { token: t })).s === 422, 'enviar incompleto -> 422');
  ok((await up('IDENTIFICACION_FRENTE', Buffer.from('texto plano largo que no es imagen'), 'a.pdf', 'application/pdf')).s === 422, 'documento con contenido inválido -> 422');
  ok((await up('FOTOS_HOGAR', pdf, 'f.pdf', 'application/pdf')).s === 422, 'FOTOS_HOGAR no acepta PDF');
  ok((await up('IDENTIFICACION_FRENTE', jpg(2), 'ine.jpg', 'image/jpeg')).s === 201, 'subir identificación (frente)');
  ok((await up('IDENTIFICACION_FRENTE', jpg(2), 'ine.jpg', 'image/jpeg')).s === 409, 'mismo archivo repetido -> 409');
  r = await up('IDENTIFICACION_FRENTE', jpg(3), 'ine2.jpg', 'image/jpeg');
  ok(r.s === 201 && r.d.requisitos.find((x) => x.clave === 'IDENTIFICACION_FRENTE').archivos.length === 1, 'reemplazo de archivo único (conserva solo el vigente)');
  ok((await up('IDENTIFICACION_REVERSO', jpg(4), 'rev.png', 'image/png')).s === 201, 'identificación (reverso)');
  ok((await up('COMPROBANTE_DOMICILIO', pdf, 'luz.pdf', 'application/pdf')).s === 422, 'domicilio sin fecha de emisión -> 422');
  ok((await up('COMPROBANTE_DOMICILIO', pdf, 'luz.pdf', 'application/pdf', { fecha_emision_documento: '2020-01-01' })).s === 422, 'domicilio vencido (>3 meses) -> 422');
  const hoy = new Date().toISOString().slice(0, 10);
  ok((await up('COMPROBANTE_DOMICILIO', pdf, 'luz.pdf', 'application/pdf', { fecha_emision_documento: hoy })).s === 201, 'domicilio vigente (PDF)');
  ok((await up('FOTOS_HOGAR', jpg(5), 'h1.jpg', 'image/jpeg')).s === 201, 'foto del hogar 1 (aún no cumple mínimo)');
  r = await api('GET', `/api/solicitudes/${sid}`, { token: t });
  ok(r.d.requisitos.find((x) => x.clave === 'FOTOS_HOGAR').cumple === false, 'FOTOS_HOGAR requiere mínimo 2');
  r = await up('FOTOS_HOGAR', jpg(6), 'h2.jpg', 'image/jpeg');
  ok(r.d.requisitos.find((x) => x.clave === 'FOTOS_HOGAR').cumple === true, 'FOTOS_HOGAR cumple con 2');
  const idArch = r.d.requisitos.find((x) => x.clave === 'COMPROBANTE_DOMICILIO').archivos[0].id_archivo;
  const dl = await fetch(`${base}/api/archivos/${idArch}`, { headers: { Authorization: `Bearer ${t}` } });
  ok(dl.status === 200 && dl.headers.get('content-type') === 'application/pdf', 'descarga protegida del documento propio');
  ok((await fetch(`${base}/api/archivos/${idArch}`, { headers: { Authorization: `Bearer ${await login('ana@adopta.com')}` } })).status === 404, 'otro usuario no puede descargar mi documento');

  const info = { regimen_vivienda: 'Rentada', arrendador_permite_mascotas: true, tiene_espacio_exterior: true, espacio_exterior_cercado: true, superficie_m2: 90,
    num_personas_hogar: 3, hay_menores: false, hogar_de_acuerdo: true, horas_solo_dia: 'De 4 a 8 horas', lugar_dormir: 'Dentro de casa', presupuesto_mensual: '$500 a $1,000',
    tiene_otras_mascotas: true, cantidad_otras_mascotas: 1, especies_otras_mascotas: 'Gato', otras_esterilizadas: true, otras_vacunadas: true,
    acepta_visita_domiciliaria: true, acepta_seguimiento: true, acepta_aviso_privacidad: true, declaracion_veracidad: true, firma_nombre: 'Carlos Ruiz', estado_formulario: 'Enviado' };
  r = await api('PUT', `/api/solicitudes/${sid}/informacion`, { token: t, json: { regimen_vivienda: null, tiene_espacio_exterior: null, num_personas_hogar: null, estado_formulario: 'Borrador' } });
  ok(r.s === 200 && r.d.informacion.estado_formulario === 'Borrador', 'guardar borrador con campos vacíos (nulls) no rompe');
  ok((await api('PUT', `/api/solicitudes/${sid}/informacion`, { token: t, json: { ...info, acepta_visita_domiciliaria: false } })).s === 422, 'información: exige aceptar visita domiciliaria');
  r = await api('PUT', `/api/solicitudes/${sid}/informacion`, { token: t, json: info });
  const claves = r.d.requisitos.map((x) => x.clave);
  ok(r.s === 200 && claves.includes('CARTA_ARRENDADOR') && claves.includes('CARTILLA_VACUNACION') && claves.includes('COMPROBANTE_ESTERILIZACION'), 'información del hogar agrega requisitos condicionales (arrendador, cartilla, esterilización)');
  ok((await api('POST', `/api/solicitudes/${sid}/enviar`, { token: t })).s === 422, 'enviar sin cartas ni referencias -> 422');
  Object.assign(rq, Object.fromEntries(r.d.requisitos.map((x) => [x.clave, x.id_requisito])));
  await up('CARTA_ARRENDADOR', jpg(7), 'carta.jpg', 'image/jpeg');
  await up('CARTILLA_VACUNACION', pdf, 'cartilla.pdf', 'application/pdf');
  const ref = (n) => api('POST', `/api/solicitudes/${sid}/referencias`, { token: t, json: { nombre_completo: `Persona Referencia ${n}`, telefono: '4771112233', relacion: 'Amigo', anios_conocidos: 5 } });
  ok((await api('POST', `/api/solicitudes/${sid}/referencias`, { token: t, json: { nombre_completo: 'A', telefono: 'x' } })).s === 422, 'referencia inválida -> 422');
  await ref(1);
  r = await api('POST', `/api/solicitudes/${sid}/enviar`, { token: t });
  ok(r.s === 422 && r.d.detalles.referencias, 'enviar con 1 referencia -> 422 (mínimo 2)');
  await ref(2);
  r = await api('POST', `/api/solicitudes/${sid}/enviar`, { token: t });
  ok(r.s === 200 && r.d.estado === 'En revision' && r.d.fecha_envio_documentos && !r.d.puede_editar_documentos, 'enviar documentación -> En revision');
  ok((await up('FOTOS_HOGAR', jpg(8), 'h3.jpg', 'image/jpeg')).s === 409, 'ya no admite cambios después de enviar');

  console.log('\nMi hogar, seguimiento y devoluciones (RF028, RF031-RF033, RF037)');
  const ta = await login('ana@adopta.com');
  r = await api('GET', '/api/adopciones', { token: ta });
  ok(r.d.items.length === 1 && r.d.items[0].nombre === 'Toby' && r.d.items[0].estado_adaptacion === 'En adaptacion', 'mis mascotas adoptadas con estado de adaptación');
  ok((await api('GET', '/api/adopciones', { token: t })).d.items.length === 0, 'otro adoptante no ve adopciones ajenas');
  const aid = r.d.items[0].id_adopcion;
  r = await api('GET', `/api/adopciones/${aid}`, { token: ta });
  ok(r.d.reportes.length === 1 && r.d.historial_adaptacion.length === 1, 'detalle: historial de reportes y de adaptación');
  ok((await api('POST', `/api/adopciones/${aid}/reportes`, { token: ta, form: new FormData() })).s === 422, 'reporte vacío -> 422');
  const fr = new FormData(); fr.append('comportamiento', 'Juega mucho'); fr.append('salud', 'Excelente');
  fr.append('fotos', new Blob([jpg(9)], { type: 'image/jpeg' }), 'a.jpg'); fr.append('fotos', new Blob([jpg(10)], { type: 'image/png' }), 'b.jpg');
  ok((await api('POST', `/api/adopciones/${aid}/reportes`, { token: ta, form: fr })).s === 201, 'registrar reporte con 2 fotos');
  r = await api('GET', `/api/adopciones/${aid}`, { token: ta });
  ok(r.d.reportes.length === 2 && r.d.reportes[0].fotos.length === 2, 'el reporte aparece primero con sus fotos');
  ok((await api('POST', '/api/devoluciones', { token: ta, json: { id_adopcion: aid, motivos: [], explicacion: 'x' } })).s === 422, 'devolución sin motivo -> 422');
  r = await api('POST', '/api/devoluciones', { token: ta, json: { id_adopcion: aid, motivos: [1, 2], explicacion: 'Me mudo a un lugar sin mascotas' } });
  ok(r.s === 201 && r.d.estado === 'Pendiente' && r.d.motivos.length === 2, 'registrar devolución con 2 motivos');
  ok((await api('POST', '/api/devoluciones', { token: ta, json: { id_adopcion: aid, motivos: [1], explicacion: 'Otra vez la misma' } })).s === 409, 'una sola devolución por adopción -> 409');
  ok((await api('GET', '/api/devoluciones', { token: ta })).d.items.length === 1, 'consultar mis devoluciones');
  const [[est]] = await pool.query("SELECT em.nombre FROM mascotas m JOIN estados_mascota em USING(id_estado_mascota) WHERE m.nombre='Toby'");
  ok(est.nombre === 'En proceso de devolucion', 'la mascota pasa a "En proceso de devolucion" (RNF015)');
  ok((await api('POST', `/api/adopciones/${aid}/reportes`, { token: ta, json: {} })).s !== 201, 'no se reporta mientras hay devolución');

  console.log('\nComunidad (RF038-RF042)');
  const fp = new FormData(); fp.append('contenido', 'Mi primer post'); fp.append('imagen', new Blob([jpg(11)], { type: 'image/jpeg' }), 'p.jpg');
  r = await api('POST', '/api/publicaciones', { token: t, form: fp });
  ok(r.s === 201 && r.d.es_mia && r.d.imagen, 'crear publicación con foto');
  const pid = r.d.id_publicacion;
  ok((await api('POST', '/api/publicaciones', { token: t, json: { contenido: '  ' } })).s === 422, 'publicación vacía -> 422');
  ok((await api('GET', '/api/publicaciones', { token: t })).d.items.length === 3, 'feed con publicaciones de la comunidad');
  const pe = new FormData(); pe.append('contenido', 'Editado');
  ok((await api('PUT', `/api/publicaciones/${pid}`, { token: ta, form: pe })).s === 403, 'no se edita una publicación ajena -> 403');
  const pe2 = new FormData(); pe2.append('contenido', 'Editado'); pe2.append('quitar_imagen', 'true');
  r = await api('PUT', `/api/publicaciones/${pid}`, { token: t, form: pe2 });
  ok(r.s === 200 && r.d.contenido === 'Editado' && r.d.imagen === null, 'editar publicación propia y quitar imagen');
  r = await api('POST', `/api/publicaciones/${pid}/like`, { token: ta });
  ok(r.d.me_gusta && r.d.likes === 1, 'Me gusta');
  ok((await api('POST', `/api/publicaciones/${pid}/like`, { token: ta })).d.likes === 0, 'quitar Me gusta');
  r = await api('POST', `/api/publicaciones/${pid}/comentarios`, { token: ta, json: { contenido: '¡Bienvenido!' } });
  ok(r.s === 201 && r.d.es_mio, 'comentar');
  const cid = r.d.id_comentario;
  ok((await api('PUT', `/api/comentarios/${cid}`, { token: t, json: { contenido: 'hack' } })).s === 403, 'no se edita un comentario ajeno -> 403');
  ok((await api('PUT', `/api/comentarios/${cid}`, { token: ta, json: { contenido: 'Bienvenido!!' } })).d.contenido === 'Bienvenido!!', 'editar comentario propio');
  ok((await api('GET', `/api/publicaciones/${pid}/comentarios`, { token: t })).d.items.length === 1, 'listar comentarios');
  ok((await api('DELETE', `/api/comentarios/${cid}`, { token: t })).s === 403, 'no se elimina un comentario ajeno');
  ok((await api('DELETE', `/api/comentarios/${cid}`, { token: ta })).s === 200, 'eliminar comentario propio');
  ok((await api('DELETE', `/api/publicaciones/${pid}`, { token: ta })).s === 403, 'no se elimina una publicación ajena');
  ok((await api('DELETE', `/api/publicaciones/${pid}`, { token: t })).s === 200, 'eliminar publicación propia');

  console.log('\nNotificaciones (RF043)');
  r = await api('GET', '/api/notificaciones', { token: t });
  ok(r.d.items.length >= 3 && r.d.no_leidas === r.d.items.filter((n) => !n.leida).length, 'notificaciones generadas por solicitudes y documentos');
  ok(r.d.items.some((n) => n.tipo === 'Documentos' && n.id_solicitud === sid), 'notificación de documentos enlaza a la solicitud');
  const antes = (await api('GET', '/api/notificaciones/no-leidas', { token: t })).d.total;
  await api('POST', `/api/notificaciones/${r.d.items[0].id_notificacion}/leida`, { token: t });
  ok((await api('GET', '/api/notificaciones/no-leidas', { token: t })).d.total === antes - 1, 'marcar como leída');
  await api('POST', '/api/notificaciones/leer-todas', { token: t });
  ok((await api('GET', '/api/notificaciones/no-leidas', { token: t })).d.total === 0, 'marcar todas como leídas');
  const [[orgN]] = await pool.query("SELECT COUNT(*) n FROM notificaciones n JOIN usuarios u USING(id_usuario) WHERE u.correo='patitas@adopta.com'");
  ok(orgN.n >= 2, 'la organización también recibe avisos');

  console.log(`\n${total - fails}/${total} pruebas correctas`);
  server.close(); await pool.end();
  process.exit(fails ? 1 : 0);
})().catch((e) => { console.error(e); process.exit(1); });
