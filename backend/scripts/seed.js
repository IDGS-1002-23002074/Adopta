// Datos de demostración. Todas las cuentas usan la contraseña: Adopta123
const bcrypt = require('bcryptjs');
const { pool, q, tx } = require('../src/db');
const cat = require('../src/catalogos');

const PASS = 'Adopta123';

(async () => {
  const hash = await bcrypt.hash(PASS, 10);
  const rol = { A: await cat.id('rol', 'Adoptante'), O: await cat.id('rol', 'Organizacion'), S: await cat.id('rol', 'Administrador') };

  await tx(async (c) => {
    const user = async (idRol, nombre, ap, am, correo, tel, foto = null) =>
      (await q('INSERT INTO usuarios (id_rol,nombre,apellido_paterno,apellido_materno,correo,contrasena,telefono,foto_perfil) VALUES (?,?,?,?,?,?,?,?)',
        [idRol, nombre, ap, am, correo, hash, tel, foto], c)).insertId;

    await user(rol.S, 'Arleth', 'Morán', 'Hernández', 'admin@adopta.com', '4771110000');
    const orgs = {};
    const orgDefs = [
      ['patitas', 'Refugio Patitas Felices', 'León', 'Av. Los Pinos 123', 'perfiles/org_patitas.jpg', 'Refugio dedicado al cuidado y protección de animales en situación de calle. Buscamos familias responsables para cada uno de nuestros habitantes.'],
      ['huellas', 'Huellas de Amor', 'Irapuato', 'Calle Hidalgo 45', null, 'Rescatamos y rehabilitamos perros y gatos para darles una segunda oportunidad.'],
      ['vida', 'Vida Animal', 'Silao', 'Blvd. Miguel Alemán 800', 'perfiles/org_vida.jpg', 'Asociación civil enfocada en la adopción responsable.'],
      ['hogar', 'Hogar Peludo', 'Guanajuato', 'Camino Real 12', null, 'Casa hogar para mascotas rescatadas.']
    ];
    for (const [k, nombre, ciudad, dir, logo, desc] of orgDefs) {
      const uid = await user(rol.O, nombre.split(' ')[0], nombre.split(' ').slice(1).join(' ') || 'Org', null, `${k}@adopta.com`, '4772223333', logo);
      const r = await q(`INSERT INTO organizaciones (id_usuario,nombre,descripcion,direccion,ciudad,codigo_postal,telefono,correo_contacto,estado)
        VALUES (?,?,?,?,?,?,?,?, 'Activa')`, [uid, nombre, desc, dir, ciudad, '37000', '4772223333', `contacto@${k}.org`], c);
      orgs[k] = { id: r.insertId, uid };
    }
    const ana = await user(rol.A, 'Ana María', 'López', 'Torres', 'ana@adopta.com', '+52 477 123 1234');
    const roberto = await user(rol.A, 'Roberto', 'Murillo', null, 'roberto@adopta.com', '4775556666');
    await q('INSERT INTO perfiles_adoptantes (id_usuario,ciudad,tipo_vivienda,tiene_mascotas,experiencia_mascotas,ocupacion) VALUES (?,?,?,?,?,?)',
      [ana, 'León', 'Casa', true, 'He tenido perros y gatos toda mi vida.', 'Diseñadora'], c);
    await q('INSERT INTO perfiles_adoptantes (id_usuario) VALUES (?)', [roberto], c);

    const est = { disp: await cat.id('estadoMascota', 'Disponible'), seg: await cat.id('estadoMascota', 'En seguimiento') };
    const ad = { sin: await cat.id('estadoAdaptacion', 'Sin seguimiento'), en: await cat.id('estadoAdaptacion', 'En adaptacion') };
    const pets = {};
    const pet = async (key, org, nombre, especie, raza, sexo, edad, tam, color, peso, desc, comp, salud, vac, ester, foto, estado = est.disp, adapt = ad.sin) => {
      const r = await q(`INSERT INTO mascotas (id_organizacion,id_estado_mascota,id_estado_adaptacion,nombre,especie,raza,sexo,edad_aproximada,tamano,color,peso,descripcion,comportamiento,estado_salud,vacunado,esterilizado)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`, [orgs[org].id, estado, adapt, nombre, especie, raza, sexo, edad, tam, color, peso, desc, comp, salud, vac, ester], c);
      pets[key] = r.insertId;
      if (foto) await q('INSERT INTO fotos_mascota (id_mascota,ruta_foto,es_principal) VALUES (?,?,TRUE)', [r.insertId, foto], c);
    };
    await pet('max', 'patitas', 'Max', 'Perro', 'Beagle', 'Macho', '2 años', 'Mediano', 'Café y blanco', 12.5,
      'Max es un perrito de raza Beagle lleno de energía, alegría y amor para dar. Tiene 2 años, tamaño mediano y se encuentra con su esquema de vacunas al día y esterilizado. Es muy amigable, juguetón y le encanta convivir con las personas. Busca un hogar responsable donde le brinden cariño, paseos diarios y un espacio seguro para crecer feliz.',
      'Cariñoso, juguetón y sociable.', 'Saludable. Desparasitado.', true, true, 'mascotas/pet_max.jpg');
    await pet('luna', 'patitas', 'Luna', 'Gato', 'Atigrado', 'Hembra', '1 año', 'Pequeno', 'Gris atigrado', 3.8,
      'Luna es cariñosa, juguetona y se lleva bien con otros gatos. Busca un hogar tranquilo donde pueda sentirse segura.',
      'Tranquila y curiosa.', 'Saludable.', true, true, 'mascotas/pet_luna.jpg');
    await pet('pinky', 'patitas', 'Pinky', 'Perro', 'Pomerania', 'Hembra', '1 año', 'Pequeno', 'Naranja', 2.9,
      'Pinky es una pomerania mimada y curiosa. Le encanta que la carguen y es perfecta para departamento.',
      'Mimosa y sociable.', 'Saludable.', true, false, 'mascotas/pet_pinky.jpg');
    await pet('miel', 'huellas', 'Miel', 'Gato', 'Siamés', 'Hembra', '1 año', 'Pequeno', 'Crema', 3.5, 'Miel es curiosa y muy comunicativa. Le encanta trepar y dormir al sol.', 'Curiosa y hablantina.', 'Saludable.', true, true, null);
    await pet('nala', 'vida', 'Nala', 'Perro', 'Pastor mestizo', 'Hembra', '1 año', 'Grande', 'Negro y fuego', 22, 'Nala es inteligente y aprende rápido. Busca una familia que disfrute del aire libre.', 'Activa y protectora.', 'Saludable.', true, false, null);
    await pet('rocky', 'hogar', 'Rocky', 'Perro', 'Labrador', 'Macho', '3 años', 'Grande', 'Dorado', 30, 'Rocky adora salir a correr y es muy sociable con niños. Necesita espacio y paseos diarios.', 'Activo y leal.', 'Saludable.', true, true, null);
    await pet('coco', 'huellas', 'Coco', 'Perro', 'Schnauzer', 'Macho', '4 años', 'Pequeno', 'Gris', 7, 'Coco es un compañero ideal para departamento: silencioso, limpio y muy apegado.', 'Apegado y silencioso.', 'Revisión dental pendiente.', true, true, null);
    await pet('pelusa', 'patitas', 'Pelusa', 'Gato', 'Angora mestiza', 'Hembra', '8 meses', 'Pequeno', 'Blanco', 2.4, 'Pelusa es una gatita llena de energía que convierte todo en juguete.', 'Juguetona.', 'Saludable.', true, false, null);
    await pet('thor', 'hogar', 'Thor', 'Perro', 'Husky mestizo', 'Macho', '2 años', 'Grande', 'Gris y blanco', 25, 'Thor tiene energía de sobra y mucha personalidad. Ideal para quien disfruta del senderismo.', 'Aventurero.', 'Saludable.', true, true, null);
    await pet('toby', 'patitas', 'Toby', 'Perro', 'Criollo', 'Macho', '2 años', 'Mediano', 'Caramelo', 14, 'Toby ya encontró hogar.', 'Tranquilo.', 'Saludable.', true, true, null, est.seg, ad.en);

    // Ana: favorito, solicitud con documentos pendientes y una adopción (Toby) con seguimiento
    await q('INSERT INTO favoritos (id_usuario,id_mascota) VALUES (?,?)', [ana, pets.max], c);
    const E = async (n) => cat.id('estadoSolicitud', n);

    const s1 = (await q(`INSERT INTO solicitudes_adopcion (id_mascota,id_adoptante,fecha_solicitud,motivo_adopcion,experiencia_previa,condiciones_vivienda,id_estado_solicitud,fecha_limite_documentos)
      VALUES (?,?,NOW() - INTERVAL 2 DAY,?,?,?,?,CURDATE() + INTERVAL 13 DAY)`,
      [pets.luna, ana, 'Busco una compañera tranquila para mi casa.', 'He tenido gatos.', 'Casa con patio cercado.', await E('Documentacion pendiente')], c)).insertId;
    await q('INSERT INTO historial_solicitud (id_solicitud,id_estado_solicitud,observacion) VALUES (?,?,?),(?,?,?)',
      [s1, await E('Pendiente'), 'Solicitud enviada por el adoptante', s1, await E('Documentacion pendiente'), 'Se solicitó la documentación para continuar'], c);
    for (const clave of ['IDENTIFICACION_FRENTE', 'IDENTIFICACION_REVERSO', 'COMPROBANTE_DOMICILIO', 'FOTOS_HOGAR']) {
      await q('INSERT INTO requisitos_solicitud (id_solicitud,id_tipo_documento) VALUES (?,?)', [s1, await cat.id('tipoDocumento', clave)], c);
    }
    const s2 = (await q(`INSERT INTO solicitudes_adopcion (id_mascota,id_adoptante,fecha_solicitud,motivo_adopcion,id_estado_solicitud,fecha_resolucion,observaciones_organizacion,documentacion_completa)
      VALUES (?,?,NOW() - INTERVAL 40 DAY,?,?,NOW() - INTERVAL 30 DAY,?,TRUE)`, [pets.toby, ana, 'Quiero un compañero para mi familia.', await E('Aprobada'), '¡Felicidades! Aprobada tras la visita.'], c)).insertId;
    await q('INSERT INTO historial_solicitud (id_solicitud,id_estado_solicitud,observacion,fecha_cambio) VALUES (?,?,?,NOW() - INTERVAL 40 DAY),(?,?,?,NOW() - INTERVAL 35 DAY),(?,?,?,NOW() - INTERVAL 30 DAY)',
      [s2, await E('Pendiente'), 'Solicitud enviada', s2, await E('En revision'), 'Documentación recibida', s2, await E('Aprobada'), 'Aprobada tras la visita'], c);
    const adop = (await q('INSERT INTO adopciones (id_solicitud,fecha_adopcion,observaciones) VALUES (?,CURDATE() - INTERVAL 28 DAY,?)', [s2, 'Entrega sin incidentes.'], c)).insertId;
    await q('INSERT INTO reportes_seguimiento (id_adopcion,comportamiento,salud,alimentacion,adaptacion,observaciones,fecha_reporte) VALUES (?,?,?,?,?,?,NOW() - INTERVAL 7 DAY)',
      [adop, 'Se muestra tranquilo y juega con la familia.', 'Sin molestias.', 'Come dos veces al día.', 'Ya conoce su cama y sus paseos.', 'Todo bien.'], c);
    await q('INSERT INTO historial_adaptacion (id_mascota,id_estado_adaptacion,id_usuario_responsable,observaciones,fecha_cambio) VALUES (?,?,?,?,NOW() - INTERVAL 20 DAY)',
      [pets.toby, ad.en, orgs.patitas.uid, 'Inicia el seguimiento post-adopción.'], c);

    const nt = (uid, t, m, tipo, sol, leida = false) => q('INSERT INTO notificaciones (id_usuario,titulo,mensaje,tipo,id_solicitud,leida) VALUES (?,?,?,?,?,?)', [uid, t, m, tipo, sol, leida], c);
    await nt(ana, 'Sube tus documentos', 'Para continuar con tu solicitud de Luna necesitamos tu documentación.', 'Documentos', s1);
    await nt(ana, 'Solicitud aprobada', 'Tu solicitud de adopción para Toby fue aprobada.', 'Solicitudes', s2, true);
    await nt(ana, 'Registra el avance de Toby', 'Cuéntanos cómo va su adaptación.', 'Seguimiento', null, true);

    // Comunidad
    const p1 = (await q('INSERT INTO publicaciones (id_usuario,contenido) VALUES (?,?)', [ana, '¡Toby cumplió un mes en casa! Ya conoce todos los rincones del parque 🐶']  , c)).insertId;
    const p2 = (await q('INSERT INTO publicaciones (id_usuario,contenido) VALUES (?,?)', [roberto, '¿Alguien tiene consejos para la primera semana con un gato adoptado?'], c)).insertId;
    await q('INSERT INTO likes_publicacion (id_publicacion,id_usuario) VALUES (?,?),(?,?)', [p1, roberto, p2, ana], c);
    await q('INSERT INTO comentarios (id_publicacion,id_usuario,contenido) VALUES (?,?,?),(?,?,?)',
      [p1, roberto, '¡Qué bonito! Felicidades 🎉', p2, ana, 'Dale un espacio tranquilo y mucha paciencia, ¡funciona!'], c);
  });
  console.log('Datos de demostración cargados.\n  Adoptante: ana@adopta.com / Adopta123\n  Adoptante: roberto@adopta.com / Adopta123');
  await pool.end();
})().catch((e) => { console.error('Error en el seed:', e); process.exit(1); });
