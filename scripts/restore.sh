#!/bin/sh
# INNOTEX Sadə Uçot: .dump faylından bazanı bərpa edir.
# İstifadə: scripts/restore.sh backups/sade_20260930_0230.dump
# DİQQƏT: cari baza SİLİNİB nüsxədəki məlumatla əvəz olunur.
set -e
[ -n "$1" ] && [ -f "$1" ] || { echo "İstifadə: $0 fayl.dump"; exit 1; }
cd "$(dirname "$0")/.."
DB_USERNAME=$(grep -E '^DB_USERNAME=' .env | cut -d= -f2-)
[ -n "$DB_USERNAME" ] || { echo ".env-də DB_USERNAME tapılmadı"; exit 1; }

echo "Cari baza silinəcək və $1 faylından bərpa olunacaq. Davam üçün BƏLİ yazın:"
read -r ans
[ "$ans" = "BƏLİ" ] || { echo "Ləğv edildi."; exit 1; }

docker compose up -d postgres
docker compose stop backend frontend backup
docker compose exec -T postgres psql -U "$DB_USERNAME" -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS sade WITH (FORCE)" -c "CREATE DATABASE sade OWNER $DB_USERNAME"
docker compose exec -T postgres pg_restore -U "$DB_USERNAME" -d sade --no-owner --exit-on-error < "$1"
docker compose up -d
echo "Bərpa tamamlandı."
