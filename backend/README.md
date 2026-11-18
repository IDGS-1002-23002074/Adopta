# Adopta+ API (Node.js + MySQL)

API REST que conecta la app móvil con la base de datos `adopta_db` (`db/Adopta_DB_completa.sql`).
La app **no** se conecta directo a MySQL: pasa por esta API (RNF001, RNF002, RNF016, RNF021).
El portal web puede usar la misma API / base de datos.

## Puesta en marcha

```bash
cd backend
cp .env.example .env        # ajusta DB_USER / DB_PASSWORD / JWT_SECRET
npm install
npm run db:reset            # crea adopta_db con el SQL y carga datos de demostración
npm start                   # http://localhost:3000   (GET /api/health comprueba la BD)
npm run smoke               # 95 pruebas de punta a punta contra la BD (úsalo en una BD de prueba)
```

Requisitos: Node 18+ y MySQL 8 / MariaDB 10.5+. `npm run db:reset` **borra y recrea** `adopta_db`.

Cuentas de demostración (contraseña `Adopta123`): `ana@adopta.com` (tiene solicitud con documentos
pendientes, una mascota adoptada con seguimiento y favoritos) y `roberto@adopta.com`.
`patitas@adopta.com`, `huellas@adopta.com`... son organizaciones y `admin@adopta.com` el administrador:
**no** pueden entrar a la app móvil (solo adoptantes, RNF001) y sirven para el portal web.

## Decisiones importantes

| Tema | Decisión |
|---|---|
| Contraseñas | bcrypt (coste 10). Sesión con JWT (`JWT_EXPIRES`, 7 días por defecto); la cuenta se revalida en cada petición (si la bloquean, pierde el acceso). |
| Recuperación de contraseña | Código de 6 dígitos (HMAC) ligado a la contraseña actual, válido 10–20 min y de un solo uso. No requiere tablas nuevas. En desarrollo se devuelve en la respuesta (`DEV_EXPOSE_RESET_CODE`); en producción se envía por correo (configura `SMTP_*`). |
| Documentos | Se guardan en `uploads_private/` y solo los descarga su dueño por `GET /api/archivos/:id`. Se valida el contenido real (cabecera JPG/PNG/PDF), tamaño, formatos y vigencia por tipo de documento. |
| Fotos | Públicas en `/uploads` (mascotas, perfiles, publicaciones, seguimiento). |
| Documentación al solicitar | `AUTO_SOLICITAR_DOCUMENTOS=true`: al crear la solicitud pasa a *Documentacion pendiente* con su checklist. Ponlo en `false` cuando el portal web de la organización sea quien pida los documentos. |
| Estados | Todo cambio de estado es transaccional y deja historial/notificaciones (RNF008, RNF009, RNF015). |

## Endpoints (todos bajo `/api`, JSON; salvo `auth` requieren `Authorization: Bearer <token>`)

| Módulo | Método y ruta | Req. |
|---|---|---|
| Acceso | `POST /auth/register`, `/auth/login`, `/auth/forgot`, `/auth/reset` | RF001-003 |
| Perfil | `GET/PUT /me`, `PUT /me/password`, `POST /me/foto`, `GET/PUT /me/preferencias` | RF004-005 |
| Catálogo | `GET /catalogos`, `GET /mascotas?q&especie&raza&tamano&sexo&ciudad&edad&limit&offset`, `GET /mascotas/:id` | RF015-017 |
| Favoritos | `GET /favoritos`, `POST/DELETE /favoritos/:idMascota` | RF019 |
| Solicitudes | `GET/POST /solicitudes`, `GET /solicitudes/:id`, `POST /solicitudes/:id/cancelar` | RF020, 024-026 |
| Documentación | `PUT /solicitudes/:id/informacion`, `POST/DELETE /solicitudes/:id/referencias`, `POST /solicitudes/:id/requisitos/:idReq/archivos`, `DELETE /solicitudes/:id/archivos/:idArchivo`, `POST /solicitudes/:id/enviar`, `GET /archivos/:id` | Módulo 5 |
| Seguimiento | `GET /adopciones`, `GET /adopciones/:id`, `POST /adopciones/:id/reportes` (multipart, ≤5 fotos) | RF028, 031, 032 |
| Devoluciones | `GET/POST /devoluciones` | RF033, 037 |
| Comunidad | `GET/POST /publicaciones`, `GET/PUT/DELETE /publicaciones/:id`, `POST /publicaciones/:id/like`, `GET/POST /publicaciones/:id/comentarios`, `PUT/DELETE /comentarios/:id` | RF038-042 |
| Notificaciones | `GET /notificaciones?tipo`, `GET /notificaciones/no-leidas`, `POST /notificaciones/:id/leida`, `POST /notificaciones/leer-todas` | RF043 |

Errores: `{ "error": "mensaje para el usuario", "detalles": { "campo": "motivo" } }` con 400/401/403/404/409/413/422.

## Producción
- Sirve la API detrás de HTTPS (nginx/Caddy) y pon `NODE_ENV=production`, un `JWT_SECRET` largo y `DEV_EXPOSE_RESET_CODE=false`.
- Respaldos (RNF023): `scripts/backup.sh` (mysqldump + rotación), programado con cron.
- Escalado (RNF011): la API no guarda estado en memoria; puedes correr varias instancias. Los archivos viven en disco: usa un volumen compartido u objeto (S3) si hay más de una.
