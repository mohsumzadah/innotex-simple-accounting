# INNOTEX Sadə Uçot: .dump faylından bazanı bərpa edir (Windows).
# İstifadə: powershell -ExecutionPolicy Bypass -File scripts\restore.ps1 backups\sade_20260930_0230.dump
# DİQQƏT: cari baza SİLİNİB nüsxədəki məlumatla əvəz olunur.
param([Parameter(Mandatory = $true)][string]$Dump)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
if (-not (Test-Path $Dump)) { throw "Fayl tapılmadı: $Dump" }
$dumpFull = (Resolve-Path $Dump).Path
$user = (Get-Content .env | Where-Object { $_ -match '^DB_USERNAME=' } | Select-Object -First 1) -replace '^DB_USERNAME=', ''
if (-not $user) { throw '.env-də DB_USERNAME tapılmadı' }

$ans = Read-Host "Cari baza silinəcək və $dumpFull faylından bərpa olunacaq. Davam üçün BƏLİ yazın"
if ($ans -ne 'BƏLİ') { Write-Host 'Ləğv edildi.'; exit 1 }

docker compose up -d postgres
docker compose stop backend frontend backup
docker compose exec -T postgres psql -U $user -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS sade WITH (FORCE)" -c "CREATE DATABASE sade OWNER $user"
if ($LASTEXITCODE -ne 0) { throw 'Baza yenidən yaradıla bilmədi' }
# pg_restore-a fayl konteynerə kopyalanıb verilir (PowerShell-in ikili axın problemi olmasın deyə)
docker compose cp $dumpFull postgres:/tmp/restore.dump
docker compose exec -T postgres pg_restore -U $user -d sade --no-owner --exit-on-error /tmp/restore.dump
if ($LASTEXITCODE -ne 0) { throw 'pg_restore xəta verdi' }
docker compose exec -T postgres rm -f /tmp/restore.dump
docker compose up -d
Write-Host 'Bərpa tamamlandı.'
