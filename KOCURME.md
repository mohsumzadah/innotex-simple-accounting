# Başqa kompüterə / serverə köçürmə

Bütün məlumat (xərclər, satışlar, hesablar, yüklənmiş fayllar, bank çıxarışları) PostgreSQL bazasındadır.
`data/files` qovluğu **artıq lazım deyil** (yalnız köhnə, bazaya köçürülməmiş fayllar üçün oxuma ehtiyatı idi; köçürmə başa çatıb yoxlanandan sonra silinə bilər).

## 1. Köhnə yerdə
1. Proqramda **Ayarlar -> Ehtiyat nüsxə -> Nüsxəni endir** düyməsi ilə `.dump` faylını alın
   (və ya `backups/` qovluğundakı ən son `sade_*.dump` faylını götürün).
2. Proqram qovluğunu (`innotex-sade`) `.env` faylı ilə birlikdə kopyalayın. `data/` və `backups/` kopyalamaq məcburi deyil.

## 2. Yeni yerdə
1. Docker Desktop (Windows) və ya Docker + Compose (Linux) quraşdırın.
2. Proqram qovluğunu və `.dump` faylını yerləşdirin.
3. Proqramı qaldırın: `docker compose up -d --build`
4. Bazanı bərpa edin:
   - Windows: `powershell -ExecutionPolicy Bypass -File scripts\restore.ps1 fayl.dump`
   - Linux: `scripts/restore.sh fayl.dump`

   Skript proqramı dayandırır, bazanı silib yenidən yaradır, nüsxəni yükləyir və proqramı başladır.
5. `http://localhost:8085` ünvanında köhnə e-poçt və parolla daxil olun.

## Nüsxələr
- `backups/` qovluğuna açılışda və hər gecə 02:30-da avtomatik `sade_YYYYMMDD_HHMM.dump` yazılır, 30 gündən köhnələr silinir.
- Bu qovluğu vaxtaşırı kompüterdən kənara (flash, bulud) kopyalayın: kompüter sıradan çıxsa yeganə salamat nüsxə o olacaq.
- Bərpanı yoxlamaq üçün: təsadüfi adlı müvəqqəti bazaya `pg_restore` edib sətir saylarını müqayisə edin, istehsalat bazasına toxunmayın.
