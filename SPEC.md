# INNOTEX Sadə Uçot: spesifikasiya

Bir şirkət (INNOTEX MMC) üçün sadə uçot proqramı; bir neçə istifadəçi ola bilər (rollar: bax "Dəyişiklik 14"). Debet/kredit, çoxşirkətlilik YOXDUR.
UI, şərhlər, xətalar Azərbaycan dilindədir. Pul `BigDecimal` (backend) / `string` (JSON), 2 rəqəm, HALF_UP.

## Stek
- `backend/`: Spring Boot 4.1 + Java 17 + JPA + Flyway + PostgreSQL 17 (`ddl-auto: validate`), Apache POI (Excel), spring-security-crypto (bcrypt). Testlər H2 (PostgreSQL mode) + MockMvc.
- `frontend/`: React 19 + TypeScript + Vite + react-router-dom 7, `oxlint`. Dizayn: `../innotex-muhasib/frontend/src/styles.css` (rənglər, tünd sol menyu, kartlar, cədvəllər) — eyni görünüş.
- `docker-compose.yml`: `postgres` + `backend` (xaricə açılmır) + `frontend` (nginx, `/api` → `backend:8080`), frontend portu `${APP_PORT:-8085}`. `.env.example` → `.env`.
- Giriş: ilk istifadəçi (ADMIN) `ADMIN_EMAIL` / `ADMIN_PASSWORD` mühit dəyişənlərindən ilk başlanğıcda yaradılır, qalanlarını administrator Ayarlar -> "İstifadəçilər"də yaradır. Parol bcrypt, sessiya tokeni DB-də yalnız SHA-256 (istifadəçiyə bağlı). `Authorization: Bearer <token>`.
- Fayllar PostgreSQL-dədir (`stored_file.content`, bytea, maks. 20 MB; bax "Dəyişiklik 6"). `FILES_DIR` yalnız köhnə sətirlər üçün oxuma ehtiyatıdır.

## Modullar

### 1. İdarə paneli (`/`)
Cari rüb: gəlir, xərc, mənfəət, hesablanmış mənfəət vergisi; hesab qalıqları; direktora borc; açıq satışlar mərhələ üzrə.

### 2. Hesablar və hərəkətlər
`Account`: ad, növ (`BANK` | `DIRECTOR_CARD` | `CASH`), valyuta (AZN), açılış qalığı, açılış tarixi. İxtiyari rekvizitlər (istifadəçi yazdığı kimi saxlanır): bankName, iban, bankCode, bankVoen, swift, correspondentAccount, cardNumber, note; Hesablar səhifəsində hər sahənin yanında 📋 var. Ayarlardakı şirkət rekvizitləri şablonlar üçün default bank mətni olaraq qalır.
`Movement` (hesab hərəkəti): tarix, hesab, istiqamət (`IN`/`OUT`), məbləğ AZN, **təyinat** (`purpose`), qeyd, fayl, `dealId?`, `expenseId?`.
Təyinatlar: `CUSTOMER_PAYMENT` (müştəri ödənişi), `FOUNDER_LOAN` (təsisçinin faizsiz yardımı), `CHARTER_CAPITAL` (nizamnamə kapitalı), `EXPENSE` (xərc ödənişi), `SALARY` (maaş), `TAX` (vergi/sığorta ödənişi), `BANK_FEE` (bank komissiyası), `TRANSFER` (hesablar arası), `OWNER_REPAYMENT` (direktora qaytarış), `OTHER`.
- `DIRECTOR_CARD` = direktorun şəxsi kartı ilə şirkət üçün edilən ödənişlər. Şirkətin direktora borcu = şəxsi kartdan ödənmiş xərclər − `OWNER_REPAYMENT`.
- `BANK_FEE` hərəkəti avtomatik xərc sayılır (ayrıca xərc yazmağa ehtiyac yoxdur).

### 3. Xərclər
`Expense`: `docDate` (sənəd/invoys tarixi, xərcin aid olduğu dövr; göndərilməsə `date`), `date` (ödəniş tarixi; hesab hərəkətini müəyyən edir), təchizatçı, kateqoriya, təsvir, məbləğ, valyuta (AZN/USD/EUR), məzənnə, AZN məbləğ (= məbləğ × məzənnə, redaktə oluna bilər — bankın tutduğu real məbləğ), ödənildiyi hesab, `deductible` (vergidən çıxılırmı, default true), fayl (qəbz/invoys), qeyd.
Xərc yaradılanda seçilmiş hesabda ödəniş tarixi ilə avtomatik `OUT` hərəkəti (`EXPENSE`) yaranır, silinəndə silinir.
Siyahı/`from`-`to` süzgəci və "Bu ay yazılmayıb" `docDate` ilə işləyir; formada "Sənəd tarixi (invoys)" və "Ödəniş tarixi", siyahıda sənəd tarixi əsas, ödəniş tarixi ikinci sətirdə.
**Xərc maddələri** (`ExpenseItem`, Ayarlar → "Xərc maddələri"; istifadəçi özü daxil edir, seed yoxdur): ad (unikal), təchizatçı, kateqoriya, valyuta, adi məbləğ (`defaultAmount?`), hesab (`accountId?`), `deductible`, təkrar (`MONTHLY` "Hər ay" | `NONE` "Birdəfəlik"), `active`, qeyd. `Expense.itemId?` maddəyə bağlayır; xərc formasında maddə seçiləndə təchizatçı, kateqoriya, valyuta, məbləğ, hesab və `deductible` dolur (tarix, real AZN məbləğ və fayl istifadəçidən). İstifadədə olan maddə silinmir (409, "passiv edin" təklifi).
API: `GET/POST /api/expense-items`, `PUT/DELETE /api/expense-items/{id}`; `GET /api/expense-items/missing?month=YYYY-MM` = aktiv `MONTHLY` maddələr ki, həmin ayda `itemId`-li xərci yoxdur: `[{itemId,name,vendor,defaultAmount,currency,lastAmountAzn,lastDate}]` (last = maddənin ən son xərci). Xərclər səhifəsində seçilmiş ay üçün "Bu ay yazılmayıb" kartı və "Yaz" düyməsi (formu maddədən doldurur), siyahıda maddə adı nişanla.
Kateqoriyalar: Server/Hosting, AI/Proqram təminatı, Abunəlik, Marketinq/Reklam, Rabitə, Ofis/İcarə, Nəqliyyat, Avadanlıq, Bank xərci, Dövlət rüsumu, Digər.

### 4. Əmək haqqı
`Employee`: ad, vəzifə, iş yeri (`MAIN` əsas / `SECONDARY` əlavə), müqavilə maaşı (gross), aktiv. İxtiyari rekvizitlər (köçürmə üçün, istifadəçi özü yazır): bank, IBAN, kart nömrəsi, kartın bitmə tarixi (`cardExpiry`, API-də "YYYY-AA", bazada ayın son günü), FİN, qeyd. Bitmiş və ya 60 gün ərzində bitən kartlar işçilər siyahısında Badge ilə, İdarə panelində xəbərdarlıq kartında (`expiringCards`, yalnız aktiv işçilər) və "Bank ödənişləri"ndə ⚠ ilə göstərilir.
`PayrollRate` (tarixli, `validFrom` ay `YYYY-MM`; yeni qanun = yeni sətir, köhnə dəyişmir). Başlanğıc sətri (istifadəçinin öz Excel/köhnə proqram dəyərləri, `2026-01`):
dsmfLimit 200, dsmfEmpLow 3, dsmfEmpHigh 10, dsmfErLow 22, dsmfErHigh 15, unempEmp 0.5, unempEr 0.5, medLimit 8000, medLow 2, medHigh 0.5, incomeLimit 8000, incomeExempt 200, incomeLow 3, incomeHigh 14 (faizlər %).
Hesablama (hər tutulma ayrıca 2 rəqəmə yuvarlaqlaşdırılır):
```
tier(limit, low, high) = g <= limit ? g*low : limit*low + (g-limit)*high
dsmfEmp = tier(dsmfLimit, dsmfEmpLow, dsmfEmpHigh);  dsmfEr = tier(dsmfLimit, dsmfErLow, dsmfErHigh)
med = tier(medLimit, medLow, medHigh)   (işçi və işəgötürən eyni)
base = max(0, g - (MAIN ? incomeExempt : 0));  income = base<=incomeLimit ? base*incomeLow : incomeLimit*incomeLow + (base-incomeLimit)*incomeHigh
unempEmp = g*unempEmp; unempEr = g*unempEr
net = g - dsmfEmp - unempEmp - med - income;  employerCost = g + dsmfEr + unempEr + med
```
`g` = gross × faktiki iş günü / norma gün. Norma gün aylıq cədvəldədir (`WorkCalendar`: ay → iş günü sayı, redaktə olunur; 2026 üçün default istifadəçinin maaş Excel-indəki "2026 İstehsalat Təqvimi", 5 günlük rejim: Yan 20, Fev 20, Mar 16, Apr 22, May 18, İyn 19, İyl 23, Avq 21, Sen 22, Okt 22, Noy 19, Dek 22).
`PayrollRun` (ay): sətirlər (işçi, gross, norma, faktiki gün, bütün məbləğlər), status `DRAFT`/`FINAL`. "Hesabla" aktiv işçilərdən sətir yaradır; gün redaktə olunur, yenidən hesablanır. FINAL olanda dəyişmir (yenidən DRAFT etmək olar).
Ekranda Excel-dəki kimi cədvəl + Excel ixracı (ayrıca "Bank ödənişləri" vərəqi ilə).
"Bank ödənişləri" bloku (vergi/sığorta sətirlərinin altında işçilər üzrə NET ödənişləri də var: № | İşçi | Bank | IBAN 📋 | Kart 📋 | Məbləğ 📋 | Təyinat 📋; `GET .../payments` cavabı `{taxes:[...], employees:[{employeeId,name,bankName,iban,cardNumber,amount,purpose}]}`, Excel vərəqinə də düşür) ABB Biznes-ə bir-bir köçürmək üçündür: cədvəl № | Təyinat | Büdcə kodu | Məbləğ | Ödəniş təyinatı mətni, hər dəyərin yanında 📋 (yalnız həmin dəyəri kopyalayır; məbləğ "123.45" kimi), "ödənildi" qeydi (yalnız brauzerdə, run üzrə localStorage), cəmi sətri. Kodlar `payment_code` cədvəlindədir (`INCOME_TAX, DSMF_EMP, DSMF_ER, MED_EMP, MED_ER, UNEMP_EMP, UNEMP_ER, NET`; büdcə kodları istifadəçinin Excel-indən), Ayarlar → "Ödəniş kodları"ndan dəyişir (`GET /api/payment-codes`, `PUT /api/payment-codes/{id}` {title,budgetCode,active,sortOrder}). `GET /api/payroll-runs/{id}/payments` → aktiv və məbləği > 0 olan `[{order,key,title,budgetCode,amount,purpose}]`; purpose = "Sentyabr 2026 ayı üzrə gəlir vergisi" (NET üçün "... ayı üzrə əmək haqqı").

### 5. Satışlar (lisenziya) və mərhələlər
`Customer` (UI-da **Kontragent**; API `/api/customers` = `/api/counterparties`): ad, VÖEN, növ `kind` (`CUSTOMER` default | `SUPPLIER` | `BOTH`), direktor, hüquqi ünvan, telefon, e-poçt; bank rekvizitləri: IBAN, bank adı, bank kodu (BİC), bank VÖEN (`bankVoen`), SWIFT, müxbir hesab (`correspondentAccount`); qeyd. Formda "Rekvizitləri yapışdır" (frontend parser: `Etiket: dəyər` sətirləri, ilk VÖEN şirkətin, "Bank" olan VÖEN bankın), detalda hər sahəyə 📋. Şablon dəyişənləri: `{{customer.*}}` və `{{company.*}}` (o cümlədən `bankVoen`, `correspondentAccount`; şirkətinkilər Ayarlar → Şirkətdən).
`Deal` (satış): müştəri, məhsul (default "INNOTEX e-Qaimə"), təsvir, qiymət (AZN), kompüter sayı, `stage`, qeyd və mərhələ sahələri:
| Mərhələ (`stage`) | Sahələr |
|---|---|
| `NEW` Yeni müştəri | — |
| `CONTRACT` Müqavilə | contractNo (avtomatik `INX-YYYY-PS-NNN`), contractDate |
| `PROTOCOL` Qiymət razılaşdırma protokolu (istəyə bağlı) | protocolDate |
| `ADVANCE_INVOICE` Avans qaiməsi | advanceInvoiceNo, advanceInvoiceDate, advanceAmount, fayl |
| `PAID` Ödəniş alındı | paymentDate, paymentAmount, ödəniş tapşırığı faylı → **avtomatik `IN` hərəkəti** (`CUSTOMER_PAYMENT`, seçilmiş bank hesabına) |
| `IN_PROGRESS` İcra (proqram hazırlanır) | — |
| `ACT` Təhvil-təslim aktı | actNo, actDate, imzalı akt faylı |
| `INVOICE` Qaimə | invoiceNo, invoiceDate, invoiceAmount, təsdiqlənmiş e-qaimə faylı |
| `DONE` Tamamlandı | — |
Mərhələ istənilən ardıcıllıqla dəyişə bilər (geri də). Hər mərhələ keçidi tarixçədə (`DealEvent`: tarix, köhnə→yeni, qeyd) saxlanılır.
Satış kartında: mərhələ zolağı (stepper) və seçilmiş mərhələnin paneli (bax: Dəyişiklik 5), "Satışın faylları" siyahısı.

### 6. Sənəd şablonları
`Template` (kod: `CONTRACT`, `PROTOCOL`, `ACT`): HTML mətn, `{{placeholder}}`lar ilə; Ayarlarda redaktə olunur.
Placeholder-lər: `{{company.name}} {{company.voen}} {{company.address}} {{company.director}} {{company.bank}} {{company.iban}} {{company.bankCode}} {{company.swift}} {{customer.*}}` (eyni sahələr) `{{deal.contractNo}} {{deal.contractDate}} {{deal.price}} {{deal.priceWords}} {{deal.quantity}} {{deal.unitPrice}} {{deal.computers}} {{deal.product}} {{deal.actNo}} {{deal.actDate}} {{deal.protocolDate}} {{today}}`.
`GET /api/deals/{id}/documents/{CONTRACT|PROTOCOL|ACT}` → doldurulmuş HTML (çap üçün A4 stil, brauzerdə "Çap et / PDF"). Məbləğ sözlə (Azərbaycan dilində, manat və qəpik) backend-də.
Default şablon mətnləri: müqavilə `01_.../02_MÜŞTƏRİLƏR_VƏ_SATIŞ/INNOTEX - e-Qaimə Müqavilə Şablonu.docx`-in bəndlərindən, akt `INNOTEX_Tehvil_Teslim_Akti.xlsx`-dən; **yalnız ümumi bənd mətni** götürülür, bütün rekvizitlər placeholder olur (real VÖEN/IBAN/ad koda yazılmır).

### 7. Rüblük hesabat
Vergi uçotu metodu: hesablama (`settings.tax_method = ACCRUAL`, yeganə dəstəklənən; Ayarlarda yalnız oxunur). Cavabda `method: "ACCRUAL"` və `warnings: [string]`; İdarə paneli eyni sahələri qaytarır. Hesabatda və Excel başlığında "Vergi uçotu metodu: Hesablama metodu".
`GET /api/reports/quarter?year=2026&q=3`:
- Gəlir = rübdə tarixi olan `INVOICE` mərhələli satışların `invoiceAmount` cəmi (gəlir yekun qaimə tarixində tanınır; avans gəlir deyil).
- Xərclər = `docDate`-i rübdə olan `deductible` xərclər (AZN; ödəniş tarixi deyil) + `BANK_FEE` hərəkətləri + FINAL maaş cədvəllərinin işəgötürən cəmi xərci (gross + işəgötürən ayırmaları), kateqoriya üzrə.
- Mənfəət = gəlir − xərclər. Mənfəət vergisi = max(0, mənfəət) × `profitTaxRate` (Ayarlar, default **20%**, istifadəçinin göstərişi).
- Maaş bloku (ay üzrə və cəmi): gross, gəlir vergisi, DSMF işçi/şirkət, İTS işçi/şirkət, işsizlik işçi/şirkət.
- İlin əvvəlindən cəmi (illik bəyannamə üçün) eyni strukturda: `GET /api/reports/year?year=2026`.
- Excel ixracı: `.../quarter.xlsx`, `.../year.xlsx`. Ekranda "e-taxes-a köçürmək üçün" rəqəmlər aydın bloklarla.
- `warnings`: dövrdəki (cari aya qədər) hər ay üçün, aktiv işçi varsa və maaş cədvəli DRAFT və ya yoxdursa "MM.YYYY maaş cədvəli yekunlaşdırılmayıb — hesabata daxil deyil"; qeydi "YOXLAYIN" ilə başlayan xərclər üçün ayrı xəbərdarlıq. Hesabatlar və İdarə panelində qabarıq göstərilir.
- Bank komissiyası hərəkət tarixi ilə qalır (ayrıca sənədi yoxdur).
Qeyd: proqram e-taxes-a özü göndərmir; rəqəmləri hazırlayır.

### 8. Ayarlar
Şirkət rekvizitləri, mənfəət vergisi dərəcəsi, maaş dərəcələri (tarixli), iş günü cədvəli, şablonlar, hesablar, parol dəyişmə.

## API (backend ↔ frontend müqaviləsi)
Hamısı `/api` altında, JSON, tarix `YYYY-MM-DD`, pul string. Xəta: `{ "error": "kod", "message": "Azərbaycan dilində" }`, 400/401/404/409.
```
POST /api/auth/login {email,password} → {token}      POST /api/auth/logout      GET /api/auth/me → {id,email,name,role}
POST /api/auth/password {current,new}

GET/PUT /api/settings → {companyName,voen,address,director,bank,iban,bankCode,swift,phone,email,profitTaxRate}
GET/POST /api/payroll-rates, PUT/DELETE /api/payroll-rates/{id}   (sahələr yuxarıdakı kimi + id, validFrom)
GET/PUT /api/work-calendar?year=2026 → [{month:"2026-01",days:19},...]
GET/PUT /api/templates/{code} → {code,title,html}

GET/POST /api/accounts, PUT/DELETE /api/accounts/{id} → {id,name,type,openingBalance,openingDate,balance}
GET /api/movements?accountId=&from=&to=  POST, PUT/DELETE /{id} → {id,date,accountId,accountName,direction,amount,purpose,note,fileId,dealId,expenseId}
GET /api/expenses?from=&to=  POST, PUT/DELETE /{id} → {id,date,vendor,category,description,amount,currency,rate,amountAzn,accountId,deductible,fileId,note}

GET/POST /api/employees, PUT/DELETE /{id} → {id,name,position,workplace,gross,active}
GET /api/payroll-runs → [{id,month,status,totalGross,totalNet,totalEmployerCost}]
POST /api/payroll-runs {month} → hesablanmış run;  GET /{id} → {id,month,status,normDays,lines:[{id,employeeId,name,position,workplace,gross,normDays,workedDays,accrued,income,dsmfEmp,medEmp,unempEmp,net,dsmfEr,medEr,unempEr,employerCost}],totals:{...eyni sahələr}}
PUT /api/payroll-runs/{id}/lines/{lineId} {workedDays}   POST /{id}/finalize   POST /{id}/reopen   DELETE /{id} (yalnız DRAFT)
GET /api/payroll-runs/{id}.xlsx

GET/POST /api/customers, PUT/DELETE /{id}
GET /api/deals?stage=  (sale_date desc, id desc)  POST {customerId,product,description,price,computers,saleDate?}  GET/PUT /{id}  DELETE /{id} (bax: Dəyişiklik 8)
POST /api/deals/{id}/stage {stage, fields..., accountId? (PAID üçün), note, eventDate?} → deal
GET /api/deals/{id}/events   GET /api/deals/{id}/documents/{code} → text/html
Deal: {id,customerId,customerName,product,description,price,computers,stage,contractNo,contractDate,protocolDate,advanceInvoiceNo,advanceInvoiceDate,advanceAmount,advanceFileId,paymentDate,paymentAmount,paymentFileId,paymentMovementId,actNo,actDate,actFileId,invoiceNo,invoiceDate,invoiceAmount,invoiceFileId,note,createdAt,saleDate}

- **Satış tarixi (V11).** `deal.sale_date` (NOT NULL; köhnə satışlar `coalesce(contract_date, created_at::date)` ilə dolduruldu). `saleDate` YYYY-MM-DD, verilməyəndə bu gün; keçmiş və gələcək tarixə məhdudiyyət yoxdur. Mərhələ keçidində ixtiyari `eventDate` (YYYY-MM-DD, default bu gün) `deal_event.event_date`-ə yazılır, tarixçədə `date` kimi qayıdır və həmin keçiddə boş qalan mərhələ tarixləri (müqavilə, protokol, avans, ödəniş, akt, qaimə) üçün default olur. Heç bir tarix sahəsində keçmiş tarix yoxlaması yoxdur; PAID hərəkətinin tarixi `paymentDate`-dir. UI: "Satış tarixi" (yeni satış, satış məlumatı, siyahı sütunu və çeşid), "Mərhələ tarixi".
- **Hazır sənəd yükləmə.** `POST /api/deals/{id}/documents/upload` (multipart `file` + `code` CONTRACT|PROTOCOL|ACT + ixtiyari `title`; default ad = fayl adı, uzantısız) şablonsuz sənəd yaradır (`templateId` null, `format` `DOCX` və ya `PDF`; başqa tip 400). Belə sənəddə `refill` 400 ("Şablondan yaradılmayıb"); `/upload` (eyni formatda yeni versiya), `/revert` işləyir; `/docx` PDF üçün də faylı düzgün Content-Type və adla qaytarır. UI: Sənədlər → "Hazır sənəd yüklə", Müqavilə mərhələsində "Hazır müqaviləni yüklə"; PDF iframe-də göstərilir (Endir, Yeni versiya yüklə, Əvvəlki versiyaya qayıt, Çap), şablonsuz sənədlərdə "Şablondan yenidən doldur" gizlədilir.

POST /api/files (multipart file) → {id,name,size}   GET /api/files/{id} (endirmə, Content-Disposition)

GET /api/dashboard → {quarter:{year,q,income,expenses,profit,profitTax}, accounts:[{id,name,balance}], ownerDebt, dealsByStage:{NEW:n,...}, expiringCards:[{employeeId,name,cardExpiry,daysLeft}]}
GET /api/reports/quarter?year&q  → {period:{from,to}, income, incomeItems:[{date,invoiceNo,customer,amount,kind}], expenses, expenseByCategory:[{category,amount}], bankFees, payrollCost, profit, profitTaxRate, profitTax, payroll:{months:[{month,gross,income,dsmfEmp,dsmfEr,medEmp,medEr,unempEmp,unempEr,net}], total:{...}}}
GET /api/reports/year?year → eyni struktur + quarters:[{q,income,expenses,profit,profitTax}]
GET /api/reports/quarter.xlsx?year&q   GET /api/reports/year.xlsx?year
```

## Testlər (backend, H2 + MockMvc)
Maaş hesablaması (200 AZN, 1000 AZN, 9000 AZN, əsas/əlavə iş yeri, natamam ay), satış mərhələləri (PAID → hərəkət yaranır), xərc → hərəkət, rüblük hesabat (gəlir yalnız INVOICE tarixi ilə, avans daxil deyil, zərərdə vergi 0), giriş/401, şablonun doldurulması və məbləğin sözlə yazılması.

## Dəyişiklik 1 (2026-09-30, istifadəçi) — yuxarıdakı uyğun bəndləri ƏVƏZ EDİR
1. **Avans qaiməsi istəyə bağlıdır.** `ADVANCE_INVOICE` və `PROTOCOL` mərhələləri ötürülə bilər; tipik axın: NEW → CONTRACT → (PROTOCOL) → (ADVANCE_INVOICE) → PAID (müştərinin göndərdiyi ödəniş tapşırığı yüklənir) → IN_PROGRESS (quraşdırma) → ACT (akt yaradılır, müştəriyə göndərilir, imzalı nüsxə yüklənir) → INVOICE → DONE. Stepper-də istəyə bağlı mərhələlər "ötür" kimi görünür.
2. **Şablonlar çoxludur.** `Template`: id, code (`CONTRACT` | `PROTOCOL` | `ACT`), title, html, isDefault. Eyni code üçün bir neçə şablon (məs. "Müqavilə — 2 kompüter", "Müqavilə — xidmət"). Seed: hər code üçün bir default.
   API (köhnə `/api/templates/{code}` əvəzinə): `GET /api/templates?code=` → list, `POST /api/templates`, `GET/PUT/DELETE /api/templates/{id}` → `{id,code,title,html,isDefault}`.
3. **Satışın öz sənədləri (redaktə olunan).** `DealDocument`: id, dealId, code, templateId, title, html, createdAt, updatedAt.
   - `POST /api/deals/{id}/documents {code, templateId}` → şablon placeholder-ləri şirkət + müştəri + satış məlumatı ilə doldurulur və satışa yazılır (şablon dəyişmir).
   - `GET /api/deals/{id}/documents` → list; `GET/PUT /api/deal-documents/{docId}` `{title, html}` — istifadəçi bənd əlavə edir/silir (UI: sadə WYSIWYG — contentEditable + qalın/siyahı/başlıq düymələri, və ya HTML mənbə rejimi); `DELETE /api/deal-documents/{docId}`.
   - `POST /api/deal-documents/{docId}/refill` → şablondan yenidən doldurur (təsdiq pəncərəsi ilə, əl dəyişiklikləri itir).
   - `GET /api/deal-documents/{docId}/print` → A4 çap HTML (text/html). Köhnə `GET /api/deals/{id}/documents/{code}` ləğv olunur.
   - Akt (`ACT`) və protokol eyni mexanizmlə. Müqavilə nömrəsi / akt nömrəsi / tarixləri mərhələ formasından gəlir.

## Dəyişiklik 2 (2026-09-30) — Word (.docx) şablonları, "Dəyişiklik 1" bəndlərinə əlavə
- `Template` və `DealDocument` `format` (`HTML` | `DOCX`, default HTML) və `fileId` (fayl anbarındakı .docx) alır (Flyway `V8`). DOCX şablonda `html` null-dur. `DealDocument`-də əlavə olaraq `previous_file_id` (bir addım geri) və cavabda `hasPrevious`.
- `POST /api/templates`, `PUT /api/templates/{id}`: `{code,title,format,fileId,html,isDefault}`. DOCX üçün əvvəl `POST /api/files` ilə .docx yüklənir, `fileId` verilir (fayl etibarlı .docx olmalıdır).
- DOCX şablondan sənəd yaradılanda şablon faylının surəti çıxarılır, `{{placeholder}}`-lər HTML şablonlarla eyni açar dəsti və eyni dəyərlərlə (məbləğ sözlə daxil) abzaslarda, cədvəllərdə (iç-içə də), başlıq və altlıqda doldurulur. Word mətni run-lara böldüyü üçün abzasın run mətnləri birləşdirilib axtarılır, dəyər placeholder-in başladığı run-un formatı ilə yazılır. Naməlum açar görünən qalır, boş dəyər `__________` olur. Şablon faylı dəyişmir; doldurulmuş fayl anbara yazılır və `DealDocument.fileId` ona işarə edir.
- `GET /api/deal-documents/{id}/docx` (endir, `Müqavilə <№> - <müştəri>.docx`, RFC 5987), `POST /api/deal-documents/{id}/upload` (multipart `file`, .docx; əvvəlki fayl `previous_file_id`-yə keçir), `POST .../revert` (cari və əvvəlki fayl yerini dəyişir), `POST .../refill` (şablondan yenidən doldurur, əvvəlki fayl saxlanır). DOCX üçün `/print` yoxdur: frontend `docx-preview` ilə render edib çap edir.
- UI: Ayarlar → Şablonlar: "Word şablonu yüklə", önizləmə, faylı dəyiş, endir, əsas et, sil; yer tutucu siyahısı hər biri üçün kopyala düyməsi ilə. Sənəddə DOCX redaktoru: Word önizləməsi, "Word-da endir", "Düzəlişli faylı yüklə", "Şablondan yenidən doldur", "Çap / PDF", "Əvvəlki versiyaya qayıt".
- `sablonlar/` qovluğu: hazır Word şablonları (məs. `Müqavilə - e-Qaimə (şablon).docx`, yalnız `{{...}}` yer tutucuları, real rekvizit yoxdur). Ayarlar → Şablonlar-dan yüklənir.

## Dəyişiklik 3 (2026-09-30) — Nomenklatura (məhsul/xidmət kataloqu)
- `Product` (Flyway `V9`, seed yoxdur): `id`, `name` (unikal, böyük-kiçik hərfə həssas deyil), `code` (mal/xidmət kodu, ixtiyari), `unit` (default `ədəd`), `defaultPrice?`, `defaultComputers?`, `description` (≤2000), `active`. `deal.product_id` (ixtiyari FK) əlavə olunur; köhnə `deal.product` mətni qalır.
- API: `GET /api/products?active=`, `POST`, `PUT/DELETE /api/products/{id}` → `{id,name,code,unit,defaultPrice,defaultComputers,description,active}`. Satışda istifadə olunan məhsulu silmək 409 (passiv etməyi təklif edir). Ad təkrarı 409.
- Satış: `POST/PUT /api/deals` `productId` qəbul edir; yaradılarkən `productId` verilibsə `deal.product` = məhsulun adı (sonradan mətn redaktə oluna bilər). `PUT`-da `productId: 0` bağı silir (mətn qalır). `Deal` cavabı `productId`, `productCode` qaytarır.
- Şablon yer tutucuları (HTML və DOCX): `{{product.name}}` (= satışın məhsul mətni), `{{product.code}}`, `{{product.unit}}`, `{{product.description}}`; məhsul seçilməyibsə boşdur (çapda `__________`).
- Hesabat: rüblük/illik cavab `incomeByProduct:[{product,amount}]` (INVOICE gəliri məhsul adı üzrə, məhsul yoxdursa `deal.product` mətni, o da boşdursa `—`; məbləğə görə azalan), Excel-də "Gəlir məhsul üzrə" bloku.
- UI: sol menyuda ayrıca "Nomenklatura" səhifəsi (`/products`); yeni satışda və satış məlumatında "Məhsul" seçimi (aktiv məhsullar + "— siyahıda yoxdur —"), seçəndə ad, qiymət, kompüter sayı, təsvir dolur; satış kartında məhsul kodu; Hesabatlar → "Gəlir məhsul üzrə".

## Dəyişiklik 4 (2026-09-30) — menyu və kart bitmə tarixi
- "Xərc maddələri" (/expense-items, Xərclərdən sonra) və "Nomenklatura" (/products, Kontragentlərdən sonra) Ayarlar tablarından çıxarılıb, sol menyuda ayrıca səhifələrdir.
- Migrasiya V10: employee.card_expiry (date, null ola bilər). Detallar §4 və dashboard API-sində.

## Dəyişiklik: ödəniş şərtləri və ödəniş bağlantıları
Bu bölmə §2 (hərəkət `dealId`), §5 (`PAID` mərhələsi, avans), Dəyişiklik 1 (tipik axın) və `Deal`/`Dashboard` API-sinin ödənişlə bağlı hissələrini ƏVƏZ EDİR. Flyway `V12`.

**Ödəniş şərti (`deal.payment_terms`, `advance_percent`).** `PREPAID` (100% avans, faiz=100), `PARTIAL` (qismən avans, faiz istifadəçidən 1–99, əks halda 400), `POSTPAID` (sonradan ödəniş, faiz=0; yeni satışın default-u). Köhnə satışlar: `advance_invoice_no` və ya `advance_amount` varsa `PREPAID`, yoxsa `POSTPAID`. `POST/PUT /api/deals` `paymentTerms`, `advancePercent` qəbul edir. `advanceRequired` = POSTPAID üçün 0; əks halda `advance_amount` (verilibsə; avans qaiməsində default qiymət × faiz / 100, redaktə olunur) və ya qiymət × faiz / 100.

**Ödəniş bağlantısı (`payment_allocation`).** `id, movement_id, deal_id, amount > 0, kind (ADVANCE | FINAL | OTHER), file_id? (ödəniş tapşırığı), note, created_at`. Qaydalar: yalnız `IN` hərəkət; bir hərəkətin bağlantı cəmi ≤ məbləği (aşanda 409 `artiq_bolusdurme`); bir satışda çox bağlantı, bir hərəkət bir neçə satışa bölünə bilər. `kind` verilməyəndə: avans tələb olunur və tam ödənilməyibsə `ADVANCE`, əks halda `FINAL`. `amount` verilməyəndə = min(hərəkətin boş qalığı, satışın qalığı). Satışın ödənişi = bütün bağlantıların cəmi (qiyməti aşa bilər, status `OVERPAID`). Bağlantısı olan satış silinmir (Dəyişiklik 8). Hesab qalığı yalnız hərəkətlərdən hesablanır, bağlantı onu dəyişmir.
```
GET  /api/deals/{id}/payments → {price,paid,remaining,status(UNPAID|PARTIAL|PAID|OVERPAID),advanceRequired,advancePaid,
                                 items:[{id,movementId,date,accountName,movementAmount,amount,kind,fileId,note,movementNote}]}
POST /api/deals/{id}/payments {movementId,amount?,kind?,fileId?,note?} → yuxarıdakı cavab (201)
PUT  /api/payment-allocations/{id} {amount?,kind?,fileId,note,dealId?}  (dealId = başqa satışa keçir; fileId/note tam əvəzlənir) → (yeni) satışın ödəniş cavabı
DELETE /api/payment-allocations/{id}   (ayır; hərəkət qalır)
GET  /api/movements/unallocated?direction=IN → [{id,date,accountName,amount,allocated,remaining,note}]  (təyinatı CUSTOMER_PAYMENT/OTHER, boş qalığı olanlar)
GET  /api/movements/{id}/allocations → [{id,movementId,dealId,contractNo,customerName,amount,kind,fileId,note}]
POST /api/movements {..., allocation:{dealId,amount?,kind?,fileId?,note?}}  (IN hərəkəti yaradıb eyni anda bağlayır)
```
`Movement` cavabına `allocated` (bağlı cəm) və `allocations` (IN üçün, yuxarıdakı sətirlər) əlavə olunub. Hərəkəti silmək bağlantısı olanda 409; məbləği bağlı cəmdən aşağı salmaq və ya bağlı hərəkəti `OUT` etmək 409. `movement.deal_id`, `deal.payment_date/payment_amount/payment_file_id/payment_movement_id` saxlanılır, lakin **deprecated**: məntiqdə istifadə olunmur, yazılmır. `POST /deals/{id}/stage` həmin sahələri və `accountId`-ni qəbul edib nəzərə almır; `PAID` mərhələsi artıq hərəkət yaratmır/yeniləmir.

**Migrasiya (V12).** `deal.payment_movement_id` olan hər satış üçün bağlantı (kind = PREPAID ? ADVANCE : FINAL, məbləğ = `payment_amount` və ya hərəkətin məbləği, hərəkətin məbləğindən çox olmayaraq, `file_id = payment_file_id`); ardınca `movement.deal_id` olub bağlantısı olmayan `IN` hərəkətləri də bağlanır. Köhnə məlumat itmir.

**Mərhələ zolağı (`stepper:[{key,label,optional,done,current}]` `Deal` cavabında, backend hesablayır).** Mərhələ kodları eynidir; `stage` real mərhələlər arasında sərbəst dəyişir, ödəniş addımları göstəricidir (yalnız `PAID` kodu real mərhələ ilə üst-üstə düşür):
- `PREPAID`: NEW → CONTRACT → (PROTOCOL) → ADVANCE_INVOICE → PAID "Ödəniş alındı" (`advancePaid ≥ advanceRequired`) → IN_PROGRESS → ACT → INVOICE → DONE
- `PARTIAL`: NEW → CONTRACT → (PROTOCOL) → ADVANCE_INVOICE → PAID "Avans ödənişi" (`advancePaid ≥ advanceRequired`) → IN_PROGRESS → ACT → INVOICE → `FINAL_PAYMENT` "Qalıq ödəniş" (`paid ≥ price`) → DONE
- `POSTPAID`: NEW → CONTRACT → (PROTOCOL) → IN_PROGRESS → ACT → INVOICE → PAID "Ödəniş" (`paid ≥ price`) → DONE (satış əl ilə ADVANCE_INVOICE mərhələsinə keçirilibsə, həmin addım da görünür)
`optional` PROTOCOL və ADVANCE_INVOICE-dir. `FINAL_PAYMENT` real mərhələ deyil, `stage` olaraq göndərilə bilməz. Real mərhələ addımında `done` = zolaqda cari mərhələdən əvvəl olması.

**`Deal` cavabına əlavələr:** `paymentTerms, advancePercent, advanceRequired, advancePaid, paid, remaining, paymentStatus, awaitingPayment, stepper`. `awaitingPayment` = qalıq > 0 və (mərhələ `INVOICE`/`DONE`, yaxud avans tələb olunur, tam ödənilməyib və satış `NEW/CONTRACT/PROTOCOL` mərhələsindən kənardadır və ya avans qaiməsi nömrəsi var). İdarə panelində `awaitingPayments:[{dealId,customerName,contractNo,invoiceDate,price,paid,remaining}]` (invoiceDate = qaimə, yoxdursa avans qaiməsi tarixi; ən köhnə əvvəl).

**UI.** Yeni satış/satış məlumatında "Ödəniş şərti" (100% avans / Qismən avans / Sonradan ödəniş) + PARTIAL üçün "Avans faizi" və hesablanmış avans. Satış səhifəsi: stepper DTO-dan; "Ödənişlər" kartı (qiymət/ödənilib/qalıq/status, bağlantı cədvəli: tarix, hesab, bank mətni, məbləğ, növ, ödəniş tapşırığı, Dəyiş / Ayır; "Bank ödənişini bağla" və "Yeni bank mədaxili yaz və bağla"). PAID mərhələ formasında köhnə tarix/məbləğ/hesab/fayl sahələri yoxdur (yalnız "Ödəniş bağla" düyməsi). Satışlar siyahısı: "Ödəniş şərti", "Ödənilib / Qalıq", status nişanı və "Ödəniş gözlənilir" filtri; idarə panelində "Ödəniş gözlənilən satışlar" kartı. Hesablar → hərəkətlər: IN hərəkəti üçün bağlantı statusu (bağlanıb / qismən / bağlanmayıb), mövcud bağlantılar "Ayır" ilə və "Satışa bağla".

**Testlər** (`PaymentsTest`): 50% qismən avans axını, bir hərəkətin iki satışa bölünməsi, artıq bölüşdürmə 409, ayır və başqa satışa yenidən bağla, bağlı hərəkətin silinməsi/azaldılması 409, V12 köçürmə skriptinin real bazada işlədilməsi.

## Tarix formatı (2026-09-30)

İstifadəçiyə göstərilən və daxil edilən hər tarix `dd.mm.yyyy`, tarix-vaxt `dd.mm.yyyy hh:mm` (24 saat, Asia/Baku), ay `mm.yyyy` formatındadır (UI: `DateInput`/`MonthInput`, `fmtDate`/`fmtDateTime`/`fmtMonth`; Excel və şablonlar da eyni); JSON API ISO (`YYYY-MM-DD`, `YYYY-MM`) olaraq qalır.

## Dəyişiklik 5: satış səhifəsində mərhələni öz panelindən tamamlamaq

**Stepper çipləri** kliklənir; default seçilmiş addım = cari mərhələ; cari çip vurğulu, tamamlananlar yaşıl "✓", istəyə bağlılar (PROTOCOL, ADVANCE_INVOICE) qırıq haşiyəli.

**Mərhələ paneli** (seçilmiş addıma görə):
- **Cari mərhələ:** sahələri + öz sənəd bloku + "Mərhələ tarixi" (default bu gün; bu mərhələnin tamamlandığı / növbətinin başladığı tarix, `DateInput`) + əsas düymə **"Tamamla → {növbəti addımın adı}"** (sahələri yazır və növbəti real mərhələyə keçirir; növbəti DONE-dursa "Satışı tamamla"), istəyə bağlı addımda **"Ötür"**, ikinci dərəcəli **"Yadda saxla"** (mərhələ dəyişmir, tarixçənin tarixinə toxunmur). `PAID` paneli: ödəniş xülasəsi + "Bank ödənişini bağla"; ödəniş tamamlanmayıbsa "Tamamla" təsdiq soruşur.
- **Keçmiş mərhələ** (cari addımdan əvvəl və ya `done`): eyni sahələr redaktə olunur + "Yadda saxla" (mərhələ dəyişmir), tarixçədəki tarix göstərilir və dəyişə bilər.
- **Gələcək mərhələ:** "Bu mərhələ hələ başlamayıb", sahələr yalnız oxunur, kiçik "Birbaşa bu mərhələyə keç" (təsdiqlə, `POST /stage`).

**Sənədlər öz mərhələsində.** CONTRACT / PROTOCOL / ACT panellərində sənəd bloku: sənəd yoxdursa "Şablondan yarat" (şablon seçimi, əsas şablon əvvəlcədən seçilir) və "Hazır sənədi yüklə" (.docx/.pdf); varsa ad, format, yenilənmə vaxtı, "Aç / redaktə", "Çap / PDF", "Yeni versiya yüklə" (yalnız DOCX/PDF), "Sil". ADVANCE_INVOICE və INVOICE panellərində nömrə/tarix/məbləğ + fayl, ACT-də "İmzalı akt" faylı; ödəniş tapşırığı faylları ödəniş bağlantılarındadır. Ayrıca "Sənədlər" kartı yoxdur; əvəzinə yalnız oxunan **"Satışın faylları"**: satışın bütün faylları (sənədlər, avans qaiməsi / akt / qaimə faylları, ödəniş tapşırıqları) mərhələ, tarix və "Aç"/"Endir" ilə. Sənəd yaradılması/yüklənməsi/silinməsi satışı yenidən oxuyur (stepper yenilənir).

**API (hamısı `Deal` qaytarır, sahələr `POST /stage` ilə eyni `StageReq`):**
- `POST /api/deals/{id}/fields {stage?, fields..., note?, eventDate?}`: mərhələni dəyişmədən verilən (boşdursa cari) mərhələnin sahələrini yazır; həmin mərhələnin tarixçə qeydinin tarixi (`eventDate`) və qeydi yenilənir. Keçməmiş mərhələ üçün yeni tarixçə qeydi yaranmır.
- `POST /api/deals/{id}/complete {fields..., note?, eventDate?}`: cari mərhələnin sahələrini (boşlar üçün default-larla) yazır və **stepper ardıcıllığındakı** növbəti real mərhələyə keçirir (server seçir; `FINAL_PAYMENT` göstəricidir, atlanır; PREPAID/PARTIAL: ADVANCE_INVOICE → PAID → IN_PROGRESS → ACT → INVOICE → DONE; POSTPAID: … INVOICE → PAID → DONE). Tarixçəyə `cari → növbəti` yazılır. Son mərhələdə (DONE) 400.
- `POST /api/deals/{id}/skip {note?, eventDate?}`: yalnız istəyə bağlı cari mərhələni (PROTOCOL, ADVANCE_INVOICE) məlumatsız növbətiyə keçirir, tarixçə qeydi default "Ötürüldü"; başqa mərhələdə 400.
- `POST /api/deals/{id}/stage` dəyişmir (istənilən mərhələyə birbaşa keçid; `stage` = cari olanda yadda saxlama kimi işləyir).

## Dəyişiklik 6 (2026-09-30): fayllar bazada, bank çıxarışları, ehtiyat nüsxə
- **Fayllar bazada.** Migrasiya V14: `stored_file` + `content bytea`, `content_type`, `sha256`, `created_at`. Yeni yükləmələr (`POST /api/files`, doldurulmuş Word sənədləri, çıxarışlar) yalnız bazaya yazılır; oxuma bazadandır. Diskdən oxuma yalnız `content` hələ boş olan köhnə sətirlər üçündür. Başlanğıcda `FileMigrator` (idempotent) boş `content`-i `FILES_DIR`-dən doldurur, sha256 və ölçünü yoxlayır, fayl yoxdursa ERROR loglayıb davam edir. Fayl silinəndə yalnız sətir silinir (diskə toxunulmur).
- **Çıxarışlar.** Migrasiya V15: `account_statement(id, account_id, period_from, period_to, file_id, note, created_at)` və `movement.statement_id`.
  - `GET /api/accounts/{id}/statements`, `POST /api/accounts/{id}/statements` (multipart `file`, `periodFrom`, `periodTo`, `note`), `GET /api/statements/{id}/file`, `DELETE /api/statements/{id}` (bağlı hərəkət varsa 409 `cixaris_istifade_olunub`; fayl da silinir).
  - `PUT /api/movements/{id}` qəbul edir `statementId` (boş buraxılsa mövcud bağlantı qalır; eyni hesabın çıxarışı olmalıdır, yoxsa 400). Xərcdən yaranan hərəkət üçün `PUT /api/movements/{id}/statement` `{statementId|null}`. `MovementRes` və `ExpenseRes` `statementId` qaytarır.
  - Hesablar səhifəsi: hər hesabda "Çıxarışlar" bloku ("Çıxarış yüklə"), hərəkət və xərc sətrində "çıxarış" keçidi.
- **Ehtiyat nüsxə.** `backup` servisi (postgres:17): açılışda və hər gecə 02:30-da `./backups/sade_YYYYMMDD_HHMM.dump`, 30 gün saxlanır. `GET /api/backup` (giriş tələb olunur) təzə `pg_dump -Fc` axıdır, fayl adı `innotex-sade-YYYY-MM-DD-HHmm.dump`. Ayarlar -> "Ehtiyat nüsxə" bölməsi. Bərpa: `scripts/restore.ps1` / `scripts/restore.sh`; köçürmə qaydası `KOCURME.md`.
- Testlər: `FilesAndStatementsTest` (baytların eyniliyi, sha256, çıxarış CRUD və 409, xərc hərəkəti, `/api/backup` 401).

## Dəyişiklik 7: satış axınının sadələşdirilməsi
Yeni miqrasiya yoxdur; mövcud məlumat dəyişmir.
- **Yeni satış bir pəncərədə.** `POST /api/deals` ixtiyari `startContract: true` qəbul edir; eyni tranzaksiyada: müqavilə nömrəsi (`INX-YYYY-PS-NNN`, ili satış tarixindən), `contractDate` = satış tarixi, mərhələ `CONTRACT` (tarixçə: NEW → CONTRACT), əsas (`isDefault`) `CONTRACT` şablonundan sənəd yaradılır. Şablon yoxdursa 404 və satış yaranmır (hamısı geri qaytarılır). UI: "Yeni satış" pəncərəsində kontragent seçimi + "+ Yeni kontragent" ("Rekvizitləri yapışdır" ilə), məhsul, qiymət, ödəniş şərti/avans faizi, satış tarixi və default seçili "Müqavilə nömrəsi ver və əsas şablondan müqaviləni hazırla"; yaradandan sonra satış səhifəsi müqavilə sənədinin redaktorunu/önizləməsini avtomatik açır.
- **Satış səhifəsinin yuxarı kartı (sticky).** Kontragent, məhsul, qiymət, ödəniş nişanı (ödənilib/qalıq), cari mərhələ və "Növbəti addım" mətni + əsas düymə (NEW: "Müqaviləni hazırla" = `complete` + müqavilə sənədi; CONTRACT/PROTOCOL/ADVANCE_INVOICE/IN_PROGRESS/ACT/INVOICE: "Müqaviləni hazırla|tamamla", "Protokolu hazırla", "Avans qaiməsini qeyd et", "Quraşdırmanı tamamla", "Aktı hazırla|İmzalı aktı yüklə", "Qaiməni qeyd et" mərhələ panelini açıb ora sürüşdürür; PAID: "Ödənişi bağla" (bağlama pəncərəsi) və ya ödəniş tamdırsa "Satışı bağla"). "Tamamla/Ötür"dən sonra növbəti mərhələ paneli açılır, qısa bildiriş "Növbəti: …" göstərilir; səhifə yenidən yüklənmir. "Təkrar satış" düyməsi eyni kontragent/məhsul/şərtlərlə "Yeni satış" pəncərəsini açır.
- **Ağıllı default-lar.** Akt nömrəsi `AKT-YYYY-NNN` (həmin ilin ən böyük nömrəsi + 1, ili akt tarixindən; əl ilə yazılan nömrələrə toxunulmur, redaktə olunur): ACT mərhələsində boş buraxılanda və ACT sənədi şablondan yaradılanda boş `actNo`/`actDate` (bu gün) dolur. Qaimə məbləği default = qiymət; avans qaiməsi məbləği = qiymət × avans faizi; müqavilə tarixi default (forma) = satış tarixi. Nömrələmə `Numbering` komponentindədir.
- **Ödəniş təklifləri.** `GET /api/deals/{id}/payment-suggestions` → `[{movementId,date,accountName,amount,remaining,note,score,reasons:[…]}]`, bölüşdürülməmiş `IN` mədaxillər (`/movements/unallocated` ilə eyni çoxluq), bal azalan, eyni balda tarix azalan. Bal: +50 bank mətni müqavilə nömrəsini (və ya avans/yekun qaimə nömrəsini) ehtiva edir (mətn və nömrə kiçik hərfə salınır, hərf/rəqəmdən başqa hər şey (boşluq, tire) silinərək müqayisə olunur; səbəb "müqavilə nömrəsi" / "qaimə nömrəsi"), +30 hərəkətin qalığı gözlənilən məbləğə (avans qalığı və ya satışın qalığı) bərabərdir ("məbləğ eynidir"), +20 mətn kontragentin adını və ya VÖEN-ini ehtiva edir ("kontragent adı" / "VÖEN"), +10 hərəkət tarixi avans qaiməsi və ya müqavilə tarixindən ≤14 gün fərqlənir ("tarix yaxındır"). UI: "Bank ödənişini bağla" pəncərəsi siyahını balla göstərir, səbəbləri çip kimi yazır, ən yüksək ballı (bal > 0) hərəkəti əvvəlcədən seçir; bağlama yalnız istifadəçi "Bağla"ya basanda olur.
- **Satışlar siyahısı.** "Cədvəl / Lövhə" seçimi (`localStorage` `sade.dealsView`, xəta tutulur). Lövhə sütunları: Yeni, Müqavilə (CONTRACT+PROTOCOL), Avans / Ödəniş gözlənilir (ADVANCE_INVOICE+PAID), İcra, Akt, Qaimə, Tamamlandı; kart: kontragent, məhsul, qiymət, ödənilib/qalıq çipi, mərhələdə neçə gün; vurmaq satışı açır. Axtarış (kontragent, müqavilə №, məhsul), il filtri (satış tarixinə görə), "Ödəniş gözlənilir"; süzülmüş siyahı üçün yekun: qiymət, ödənilib, qalıq. `Deal` cavabına `stageSince` (cari mərhələyə keçid tarixi, tarixçədən; yoxdursa satış tarixi) əlavə olunub.
- **Kontragent səhifəsi.** Cədvəldə satış sayı (açıq, qalıq) və "Yeni satış" (kontragent əvvəlcədən seçilir); kontragentin pəncərəsində satışlar (mərhələ, qiymət, ödənilib/qalıq, status) və "Yeni satış".
- **Testlər** (`SalesFlowTest`): `startContract` (mərhələ CONTRACT, nömrə, tarix, CONTRACT sənədi, 2 tarixçə qeydi), akt nömrəsi ardıcıllığı və əl ilə yazılan nömrənin qorunması, ödəniş təkliflərinin sırası (müqavilə nömrəsi uyğunluğu birinci; boşluq/tire fərqinə dözümlü), heç nəyin avtomatik bağlanmaması.

## Dəyişiklik 8: məhsulun ödəniş şərti, satışın silinməsi/ləğvi, maaş ödənişlərinin izlənməsi
Migrasiya `V16`. Mövcud məlumat dəyişmir.

**Məhsulun default ödəniş şərti.** `product.default_payment_terms` (`PREPAID|PARTIAL|POSTPAID`, null ola bilər) + `default_advance_percent` (PREPAID=100, POSTPAID=0, PARTIAL=1..99 məcburi; şərt yoxdursa faiz də null). `ProductReq/Res` `defaultPaymentTerms`, `defaultAdvancePercent` daşıyır. UI: Nomenklatura formasında "Default ödəniş şərti" (+ PARTIAL üçün faiz); "Yeni satış" pəncərəsində məhsul seçiləndə satışın ödəniş şərti/faizi doldurulur (redaktə olunur).

**Satışın silinməsi (§5 və "Satış silinəndə (yalnız NEW)" qaydasını ƏVƏZ EDİR).** `DELETE /api/deals/{id}` istənilən mərhələdə işləyir, yalnız satışın ödəniş bağlantısı (`payment_allocation`) yoxdursa; varsa 409 `odenis_bagli` "Əvvəlcə ödəniş bağlantılarını ayırın" (bank hərəkəti heç vaxt silinmir). Satışın sənədləri (`deal_document`), tarixçəsi və yalnız ona aid fayllar (başqa heç bir sətir işarə etmirsə; `FileService.isReferenced`) silinir.

**Satışın ləğvi.** `deal.cancelled` (default false), `cancel_reason`, `cancelled_at`; `Deal` cavabı `cancelled, cancelReason, cancelledAt, hasAllocations` qaytarır. `POST /api/deals/{id}/cancel {reason?}` (artıq ləğvdirsə 409), `POST /api/deals/{id}/restore` (ləğv deyilsə 409). Ləğv olunmuş satış: rüblük/illik gəlirə (və məhsul üzrə gəlirə), idarə panelinin mərhələ saylarına və "ödəniş gözlənilən" kartına daxil deyil, `awaitingPayment=false`; `GET /api/deals` onu yalnız `?includeCancelled=true` ilə qaytarır; UI-da siyahıda "Ləğv olunanları göstər" seçimi ilə solğun və "ləğv olunub" nişanı ilə, lövhədə və yekunlarda yoxdur. Ləğv olunmuş satışın ödəniş bağlantıları qalır (UI xəbərdarlıq edir). Satış səhifəsi: "Satışı sil" (təsdiq; bağlantı varsa söndürülür, izah ilə), "Ləğv et" / "Bərpa et".

**Maaş ödənişlərinin izlənməsi.** Cədvəllər: `payroll_payment(id, run_id, code_key, employee_id?, amount>0, movement_id?, paid_date, file_id?, note)` və `payroll_run_file(id, run_id, file_id, title, created_at)`. Kodlar `payment_code` ilə eynidir (`INCOME_TAX, DSMF_EMP, DSMF_ER, MED_EMP, MED_ER, UNEMP_EMP, UNEMP_ER, NET`); `employee_id` yalnız işçi üzrə `NET` bağlantısı üçündür.
- `GET /api/payroll-runs/{id}/payments`: hər sətirə (`taxes[]` və `employees[]`) `status` (`PAID` bağlı cəm ≥ məbləğ, `PARTIAL`, `UNPAID`), `paidAmount`, `fileId` (bağlantıların ilk faylı) və `links:[{id,movementId,date,amount,note,fileId}]` əlavə olunub. Vergi sətirlərinin `NET`-i işçi üzrə və ümumi bağlantıların cəmi ilə hesablanır.
- `POST /api/payroll-runs/{id}/payments/link {codeKey, employeeId?, movementId, amount?, fileId?, note?}` → yenilənmiş `payments` (201). Yalnız `OUT` hərəkət (400); `amount` verilməyəndə = min(hərəkətin boş qalığı, sətrin qalığı); hərəkətin boş qalığından çox (409 `artiq_bolusdurme`) və ya sətrin qalığından çox (409 `artiq_odenis`) olmaz; sətir tam ödənilibsə 409 `setir_odenilib`. Bir hərəkət bir neçə sətrə bölünə bilər. `DELETE /api/payroll-payment-links/{id}` (hərəkət qalır).
- `GET /api/payroll-runs/{id}/movement-suggestions?codeKey=&employeeId?` → `[{movementId,date,accountName,amount,remaining,purpose,note,score,reasons}]`: təyinatı `SALARY`/`TAX` olan, `BANK` hesabındakı, boş qalığı olan `OUT` hərəkətlər. Bal: +100 qalıq sətrin qalığına bərabərdir ("məbləğ eynidir"), +30 tarix dövrün 1-indən dövr sonundan 45 gün sonraya qədərdir, +20 təyinat uyğundur (NET→SALARY, qalanı→TAX), +10 qeyddə maaş/vergi açar sözü; bal azalan, sonra tarix azalan.
- `GET/POST /api/payroll-runs/{id}/files` (multipart `file` + ixtiyari `title`), `DELETE /api/payroll-runs/{id}/files/{fileRowId}` (fayl başqa yerdə işlənmirsə bazadan silinir); `RunFileRes {id,fileId,title,name,size,createdAt}`. DRAFT cədvəl silinəndə bağlantıları və sənədləri də silinir.
- İdarə paneli `unpaidPayroll:[{runId,month,lines:[{key,title,amount,paidAmount,status}]}]`: dövrü `2026-08` və sonrası olan FINAL cədvəllərin `PAID` olmayan vergi/NET sətirləri.
- UI (maaş cədvəli səhifəsi): "Bank ödənişləri"ndə hər sətirdə status çipi (ödənilib / qismən / gözlənilir), bağlı bank əməliyyatının tarixi və məbləği + "Ayır", "Bank əməliyyatını bağla" (təkliflər sıralı, ən yaxşısı əvvəlcədən seçilir, istifadəçi "Bağla"ya basır); köhnə brauzer-lokal "ödənildi" qeydi götürülüb. "Sənədlər" kartı: yüklə / aç / sil. İdarə panelində "Ödənilməmiş maaş / vergi" kartı.
- Testlər: `PayrollPaymentsTest` (bağla/ayır, status, təkliflərin sırası, artıq bölüşdürmə 409, sənədlər, idarə paneli), `DealCancelTest` (bağlantısız/bağlantılı silmə, ləğv hesabatdan çıxarır, bərpa, məhsulun default şərti).

## Dəyişiklik 9: fiziki şəxslərə satış
Migrasiya `V17` (`customer.fin`). Mövcud məlumat dəyişmir. Fiziki şəxsə (VÖEN-siz) lisenziya satılır: müqavilə, akt və e-qaimə yoxdur; alıcı ABB ödəniş linki / kart köçürməsi ilə ödəyib çek və ya ekran şəkli göndərir (yaxud sahib bank tranzaksiyasını sənəd kimi əlavə edir), sonra satış tamamlanır.
- **Kontragent növü `INDIVIDUAL`** ("Fiziki şəxs"; `CUSTOMER|SUPPLIER|BOTH|INDIVIDUAL`). VÖEN məcburi deyil (boş ola bilər); ixtiyari `fin`, telefon, e-poçt, qeyd. INDIVIDUAL üçün direktor və bank rekvizitləri saxlanmır (göndərilsə də atılır), `fin` yalnız INDIVIDUAL-dadır. `CustomerDto` `fin` daşıyır. UI: formada INDIVIDUAL seçiləndə "Rekvizitləri yapışdır", VÖEN, direktor, ünvan və bank bölməsi gizlənir; siyahıda "fiziki şəxs" nişanı və VÖEN/FİN sütunu.
- **Ödəniş şərti `RETAIL`** ("Fiziki şəxs (pərakəndə)"; `advance_percent = 0`, `advanceRequired = 0`, ödəniş `kind` default `FINAL`). Stepper: `NEW` → `PAID` "Ödəniş və çek" (`paid ≥ price`) → `IN_PROGRESS` "Quraşdırma" (istəyə bağlı, `/skip` işləyir) → `DONE`. `/complete` və `/skip` bu ardıcıllıqla gedir. Müqavilə nömrəsi verilmir (`startContract` nəzərə alınmır); `/stage` və `/fields` ilə CONTRACT/PROTOCOL/ADVANCE_INVOICE/ACT/INVOICE mərhələsi 400, sənəd yaratmaq/yükləmək 400. `awaitingPayment` = qalıq > 0. Məhsulun default şərti RETAIL ola bilməz (400). UI: yeni satışda INDIVIDUAL kontragent seçiləndə şərt RETAIL olur (dəyişmək olar), RETAIL-də müqavilə qutusu gizlənir.
- **Gəlir (hesablama).** RETAIL satış, ləğv olunmayıbsa və `DONE`-dursa gəlirdir: tarix = `DONE`-a keçidin `deal_event.event_date`-i (ən sonuncu; yoxdursa ən son ödəniş bağlantısının hərəkət tarixi, o da yoxdursa satış tarixi), məbləğ = `price`. Rüblük/illik hesabata, idarə panelinə, məhsul üzrə gəlirə və Excel-ə düşür. `incomeItems[]`-ə `kind` (`INVOICE` | `RETAIL`) əlavə olunub; RETAIL üçün `invoiceNo` null (UI "—" və "fiziki şəxs" nişanı, Excel-də "—" və "(fiziki şəxs)"). Tamamlanıb, lakin tam ödənilməyibsə `warnings`-ə "Fiziki şəxs satışı tamamlanıb, lakin tam ödənilməyib (ad, qalıq …) — gəlirə daxildir". Qaimə əsaslı gəlir məntiqi dəyişmir.
- **Sürətli satış.** `POST /api/deals/quick-retail` (multipart: `request` = JSON, ixtiyari `file` = çek / ekran şəkli / bank tranzaksiyası; yalnız PNG, JPEG, PDF, başqası 400) bir tranzaksiyada: lazımsa yeni INDIVIDUAL kontragent, `RETAIL` satış, ödəniş bağlantısı (`FINAL`, fayl ilə; yeni mədaxildə fayl hərəkətə də qoyulur) və mərhələ. `request`: `{customerId | buyer:{name,phone,email,fin}, productId?, product?, price (>0), saleDate?, movementId | movement:{accountId,date,amount,note}, installed? (default true), note?}`. Ya `movementId`, ya `movement` (ikisi birdən və ya heç biri 400); mövcud `customerId` INDIVIDUAL olmalıdır. Tarixçə: `NEW` (satış tarixi) → `PAID` → (`installed` olanda) `DONE`; PAID və DONE tarixi = max(satış tarixi, ödəniş hərəkətinin tarixi), yəni gəlir bu tarixdə tanınır. `installed=false` olanda satış `PAID`-də qalır və gəlir yoxdur. Cavab: `Deal`. Hərəkətin boş qalığı bitibsə 409 (hamısı geri qaytarılır).
- `GET /api/payment-suggestions?amount=&date=&name=` → `PaymentSuggestion[]`: hələ satışı olmayan sürətli satış üçün eyni bal qaydaları (+30 məbləğ, +20 ad, +10 tarix ≤14 gün); satış üçün `/deals/{id}/payment-suggestions` ilə eyni ranjirləmə kodudur.
- **UI.** "Sürətli satış" pəncərəsi (Satışlar səhifəsində düymə və INDIVIDUAL kontragentin sətrində/pəncərəsində): alıcı (mövcud fiziki şəxs və ya "+ Yeni alıcı"), məhsul (qiyməti doldurur), qiymət, satış tarixi, ödəniş ("Mövcud bank mədaxili" balla sıralı təkliflər, ən yaxşısı əvvəlcədən seçilir / "Yeni mədaxil": hesab, tarix, məbləğ, bank mətni), çek faylı, "Quraşdırıldı — satışı tamamla" (default açıq), qeyd. Satış səhifəsi RETAIL üçün: müqavilə/akt/qaimə bölmələri yoxdur, stepper və "Ödənişlər" kartı (çek faylı ilə) var.
- **Testlər** (`RetailSaleTest`): VÖEN-siz INDIVIDUAL, RETAIL axını (müqavilə nömrəsiz, stepper, complete/skip, qadağan mərhələlər), yeni mədaxil + fayl ilə sürətli satış → DONE, ödəniş bağlantısı, rüb hesabatında DONE tarixi ilə gəlir, ləğv gəliri çıxarır, mövcud mədaxil ilə (quraşdırılmamış → gəlir yoxdur, sonra DONE → gəlir), tamamlanıb ödənilməyib xəbərdarlığı.

## Dəyişiklik 10: hüquqi / fiziki şəxs ayrıca ölçü kimi
Migrasiya `V18` (`customer.entity_type`, `customer.card_number`). Dəyişiklik 9-dakı "kontragent növü `INDIVIDUAL`" əvəz olunur: iki ayrı ölçü var.
- **`entityType`** = `LEGAL` (default) | `INDIVIDUAL` (şəxs növü); **`kind`** = rol `CUSTOMER` | `SUPPLIER` | `BOTH`. Migrasiya: `kind='INDIVIDUAL'` sətirləri → `entity_type='INDIVIDUAL'`, `kind='CUSTOMER'`; qalanları `LEGAL`.
- API: `CustomerDto` `entityType` və `cardNumber` daşıyır. Girişdə köhnə `kind:"INDIVIDUAL"` qəbul olunur və `entityType=INDIVIDUAL`, `kind=CUSTOMER` kimi çevrilir. Yanlış rol/şəxs növü 400. Hüquqi şəxsdə ad məcburidir; fiziki şəxsdə VÖEN yoxdur.
- Saxlanan sahələr: LEGAL üçün `fin` və `cardNumber` atılır; INDIVIDUAL üçün direktor, bank adı, bank kodu, bankın VÖEN-i, SWIFT, müxbir hesab atılır, IBAN və `cardNumber` (geri ödəniş/ödəniş üçün) qalır. Sürətli satış, RETAIL və "fiziki şəxs" məntiqi `entityType`-a baxır.
- UI: `CustomerForm` yuxarıda "Hüquqi şəxs | Fiziki şəxs" seçimi (default Hüquqi; `lockType` ilə kilidlənə bilər), növ dəyişəndə ortaq sahələr (ad, telefon, e-poçt, qeyd) qalır, gizlənənlər göndərilmir. Hüquqi: rol, ad (məcburi), VÖEN (daxil edilibsə 10 rəqəm), direktor, ünvan, bank rekvizitləri, "Rekvizitləri yapışdır". Fiziki: rol, ad soyad ata adı (məcburi), FİN (7 simvol, böyük hərf), ünvan, yığılan "Bank / kart". Siyahıda filtr (Hamısı/Hüquqi/Fiziki), "hüquqi/fiziki şəxs" və rol nişanları; Yeni satışda seçimdə növ göstərilir, fiziki şəxs RETAIL-i default edir.
- Testlər (`RetailSaleTest`): köhnə `kind:INDIVIDUAL` çevrilməsi, VÖEN-siz fiziki şəxs, hüquqi şəxsdə boş ad 400, sahələrin atılması, migrasiya köçürmə SQL-i.

## Dəyişiklik: menyu və rəng rejimləri (2026-10-01)
Sol menyu qruplanıb (Satış, Maliyyə, Kadr, Hesabat), ikonlarla (lucide-react), ikon rəlsinə yığıla bilir (localStorage) və dar ekranda (<900px) çəkməcəyə çevrilir. Rəng rejimi Açıq / Qaranlıq / Sistem (localStorage, `data-theme`); sənəd önizləmələri hər rejimdə ağ kağız qalır.

## Dəyişiklik 11: satışın miqdarı və mərhələdə əlavə sənəd (2026-10-01)
Migrasiya `V19` (`deal.quantity int not null default 1`). Mövcud satışlar 1 miqdarla qalır.
- **Miqdar.** `DealReq`/`DealRes` `quantity` (≥1, əks halda 400; `POST`-da verilməsə 1, `PUT`-da verilməsə dəyişmir). Nomenklaturanın `defaultPrice` və `defaultComputers`-i **1 vahid** üçündür. UI ("Yeni satış" və "Satış məlumatı"): "Miqdar (<vahid>)" sahəsi; məhsul seçiləndə qiymət = vahid qiymət × miqdar, kompüter = vahid kompüter × miqdar; miqdar dəyişəndə qiymət və kompüter sayı əvvəlki miqdara nisbətlə mütənasib dəyişir (qiymət sonra əl ilə redaktə oluna bilər). Backend qiyməti miqdardan hesablamır.
- **Yer tutucular:** `{{deal.quantity}}`, `{{deal.unitPrice}}` (= qiymət / miqdar, 2 rəqəm).
- **"Satış məlumatı" → "Yadda saxla"** satışı, tarixçəni, sənədləri və "Ödənişlər" kartını (qiymət/ödənilib/qalıq) birlikdə yenidən oxuyur.
- **Əlavə sənəd.** Bir mərhələdə (CONTRACT / PROTOCOL / ACT) bir neçə sənəd ola bilər (backend artıq icazə verirdi). Sənəd bloku sənəd olanda da "+ Əlavə sənəd yüklə" (.docx/.pdf, ad default "Müqaviləyə əlavə №N") və "+ Şablondan əlavə et" göstərir. Mərhələnin ilk sənədi olmayan sənəd öz adı ilə endirilir (`<ad> <№> - <kontragent>.docx|pdf`).
- Testlər: `DealQuantityTest`.

## Dəyişiklik 12: hesablar səhifəsinin UX-i (2026-10-01)
Migrasiya `V20` (`account.is_default`; mövcud bazada ilk `BANK` hesabı əsas olur).
- **Əsas hesab.** `AccountRes.isDefault`; `POST /api/accounts/{id}/default` hesabı əsas edir (qalanlarından götürülür, ən çox bir). Əsas hesab yoxdursa, yaradılan ilk `BANK` hesabı əsas olur. Hesablar səhifəsi açılanda əsas hesab seçilir, kartında "əsas" nişanı, rekvizit kartında "Əsas hesab et". Yeni mədaxil (satış), sürətli satış və xərc formalarında default hesab = əsas hesab, yoxdursa ilk bank hesabı (`defaultAccount`). Xərc maddəsinin öz hesabı varsa o üstündür.
- **Satışa yalnız "Müştəri ödənişi".** Ödəniş bağlantısı (Dəyişiklik "ödəniş şərtləri" bölməsini ƏVƏZ EDİR) yalnız `IN` + təyinatı `CUSTOMER_PAYMENT` olan hərəkətə yaradılır (əks halda 400); `unallocated` və ödəniş təklifləri də yalnız bunları qaytarır. Bağlı hərəkətin təyinatını dəyişmək 409. Mövcud bağlantılar dəyişmir. UI: "Satışa bağla" və bağlantı statusu yalnız müştəri ödənişində (və ya köhnə bağlantısı olanda) görünür; formada istiqamət dəyişəndə təyinat uyğunlaşır (IN → Müştəri ödənişi, OUT → Xərc ödənişi).
- **Silmə təsdiqi.** Hesablar səhifəsində hesabın, hərəkətin silinməsi və bağlantının ayrılması brauzerin `confirm()`-i əvəzinə proqramın öz pəncərəsi (`ConfirmModal`) ilə: nə silindiyi (tarix, təyinat, məbləğ) göstərilir, xəta (məs. 409) pəncərənin içində çıxır.
- Testlər: `AccountDefaultTest`.

## Dəyişiklik 13: sənədin adının dəyişməsi (2026-10-01)
- `PUT /api/deal-documents/{id}` `{title?, html?}`: `html` göndərilməyəndə (yalnız ad) HTML sənədin mətni qalır; boş ad 400. Word/PDF faylı dəyişmir.
- UI: mərhələnin sənəd blokunda hər sənədin yanında "✎ Adını dəyiş" (yerində redaktə, Enter = yadda saxla, Esc = imtina). Word/PDF redaktorunda ad sahəsindən çıxanda yadda saxlanır və siyahı yenilənir. Endirmə adı əlavə sənədlər üçün bu addan götürülür.

## Dəyişiklik 14: istifadəçilər və rollar (2026-10-02)
Migrasiya `V21` (`app_user.name/role/active/created_at`, `session_token.user_id`; köhnə sessiyalar silinir, mövcud istifadəçi `ADMIN` olur).
- **Rollar.** `ADMIN` (administrator): hər şey + istifadəçilərin idarəsi. `ACCOUNTANT` (mühasib): bütün uçot bölmələri (ayarlar və ehtiyat nüsxə daxil), istifadəçilərin idarəsi istisna (403 `icaze_yoxdur`). Hər istifadəçi öz parolunu dəyişir (`POST /api/auth/password` cari istifadəçiyə aiddir).
- **API (yalnız ADMIN).** `GET /api/users` → `[{id,email,name,role,active,createdAt}]`; `POST /api/users {email,name,role,password,active?}` (201; e-poçt kiçik hərfə salınır, unikal: 409 `email_movcuddur`; parol ≥ 8; rol default `ACCOUNTANT`); `PUT /api/users/{id} {name?,role?,active?,password?}` (e-poçt dəyişmir; boş parol dəyişmir); `DELETE /api/users/{id}` (204).
- **Qaydalar.** Özünü silmək, passiv etmək, rolunu endirmək olmaz (409 `ozunu_deyisme`); ən azı bir aktiv `ADMIN` qalır (409 `son_admin`). Passiv istifadəçi daxil ola bilmir (401), passiv edilən / parolu sıfırlanan / silinən istifadəçinin sessiyaları bağlanır.
- **UI.** Ayarlar -> "İstifadəçilər" (yalnız administratora görünür): cədvəl (ad, e-poçt, rol, status, yaradılıb), "Yeni istifadəçi", "Dəyiş" (ad, rol, aktiv, yeni parol), "Sil" (təsdiqlə). Sol menyunun altında istifadəçinin adı (yoxdursa e-poçtu), üzərinə gələndə e-poçt və rol.
- Testlər: `UsersTest` (mühasib yaratmaq, 403, öz parolu, passiv etmək sessiyanı bağlayır, parol sıfırlama, silmə, son admin qorunur).
