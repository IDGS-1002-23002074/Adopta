#!/bin/sh
# RNF023 — Respaldo de la base de datos. Programa este script con cron (p. ej. cada noche).
# Uso: DB_USER=root DB_PASSWORD=... ./backup.sh [carpeta_destino]
DEST="${1:-./backups}"
mkdir -p "$DEST"
FILE="$DEST/adopta_db_$(date +%Y%m%d_%H%M%S).sql.gz"
mysqldump -u"${DB_USER:-root}" ${DB_PASSWORD:+-p"$DB_PASSWORD"} --single-transaction --routines --triggers adopta_db | gzip > "$FILE"
echo "Respaldo creado: $FILE"
# Conserva solo los últimos 14 respaldos
ls -1t "$DEST"/adopta_db_*.sql.gz | tail -n +15 | xargs -r rm --
