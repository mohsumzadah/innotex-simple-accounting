-- Default sənəd şablonları (A4, çap üçün HTML). Bütün rekvizitlər {{placeholder}}-dir.

insert into template (code, title, is_default, html) values ('CONTRACT', 'Müqavilə', true, '<!DOCTYPE html>
<html lang="az"><head><meta charset="utf-8"><title>Müqavilə № {{deal.contractNo}}</title>
<style>
@page { size: A4; margin: 18mm 16mm; }
body { font-family: "Times New Roman", serif; font-size: 12pt; line-height: 1.35; color: #000; margin: 0; }
.doc { max-width: 178mm; margin: 0 auto; padding: 8mm 0; }
h1 { text-align: center; font-size: 14pt; margin: 0 0 4px; }
.c { text-align: center; }
.row { display: flex; justify-content: space-between; margin: 10px 0; }
h2 { font-size: 12pt; margin: 14px 0 4px; }
p { margin: 3px 0; text-align: justify; }
table.sig { width: 100%; border-collapse: collapse; margin-top: 8px; page-break-inside: avoid; }
table.sig td { width: 50%; vertical-align: top; padding: 4px 8px 4px 0; }
</style></head><body><div class="doc">
<h1>PROQRAM TƏMİNATINDAN İSTİFADƏ HÜQUQU VƏ TEXNİKİ DƏSTƏK HAQQINDA MÜQAVİLƏ</h1>
<p class="c">№ {{deal.contractNo}}</p>
<div class="row"><span>Bakı şəhəri</span><span>{{deal.contractDate}}</span></div>
<p>Bir tərəfdən {{company.name}} (VÖEN: {{company.voen}}) (bundan sonra – “Təchizatçı”), digər tərəfdən {{customer.name}} (VÖEN: {{customer.voen}}) (bundan sonra – “Müştəri”), birlikdə “Tərəflər”, aşağıdakılar barədə bu Müqaviləni bağladılar.</p>

<h2>1. MÜQAVİLƏNİN PREDMETİ</h2>
<p>1.1. Təchizatçı Müştəriyə “{{deal.product}}” proqram təminatından (Chrome brauzer əlavəsi/extension) bu Müqavilədə nəzərdə tutulmuş şərtlərlə istifadə hüququ verir, proqramın ilkin quraşdırılmasını və sazlanmasını həyata keçirir.</p>
<p>1.2. Proqram new.e-taxes.gov.az portalında istifadəçinin öz hesabına daxil olduqdan sonra e-qaimələrlə bağlı təkrarlanan əməliyyatların sürətləndirilməsi məqsədilə nəzərdə tutulmuş köməkçi proqram vasitəsidir.</p>
<p>1.3. Proqramın əsas funksiyalarına seçilmiş tarix aralığı üzrə gələn və/və ya göndərilən e-qaimələrin axtarılması, PDF formatında toplu yüklənməsi, şirkət adı və ya VÖEN üzrə seçim, faylların avtomatik adlandırılması və seçilmiş arxiv qovluğuna yerləşdirilməsi daxildir.</p>
<p>1.4. Bu Müqavilə üzrə verilən istifadə hüququ qeyri-müstəsna, ötürülməz və yalnız Müştərinin istifadəsi üçündür. Proqramın mənbə kodu, müəlliflik hüquqları və digər əqli mülkiyyət hüquqları Müştəriyə verilmir.</p>

<h2>2. LİSENZİYA VƏ İSTİFADƏ ŞƏRTLƏRİ</h2>
<p>2.1. Lisenziya maksimum {{deal.computers}} kompüterdə istifadə üçün nəzərdə tutulur.</p>
<p>2.2. Müştəri proqramı və ya aktivasiya/lisenziya məlumatlarını üçüncü şəxslərə ötürməməli, satmamalı, icarəyə verməməli və icazəsiz surətdə yaymamalıdır.</p>
<p>2.3. Müştəri proqramın qoruma və lisenziyalaşdırma mexanizmlərini keçməyə, dəyişdirməyə və ya ləğv etməyə cəhd etməməlidir.</p>
<p>2.4. Proqram təminatı lisenziyanın verildiyi kompüterin unikal avadanlıq identifikatoruna uyğun hazırlanır və aktivləşdirilir. Kompüterin dəyişdirilməsi, formatlanması, avadanlıq komponentlərinin dəyişdirilməsi və ya digər səbəblərdən cihaz identifikatorunun dəyişməsi nəticəsində proqramın həmin cihaz üçün yenidən hazırlanması və aktivləşdirilməsi tələb olunduqda, bu iş mövcud lisenziya çərçivəsində pulsuz texniki dəstəyə daxil edilmir və ayrıca ödəniş əsasında həyata keçirilir.</p>

<h2>3. QİYMƏT VƏ ÖDƏNİŞ</h2>
<p>3.1. Proqramdan istifadə hüququnun, ilkin quraşdırma və sazlama xidmətinin ümumi birdəfəlik qiyməti {{deal.price}} AZN ({{deal.priceWords}}) təşkil edir. Müqavilə üzrə ödəniş 100% əvvəlcədən ödəniş qaydasında həyata keçirilir.</p>
<p>3.2. Ödəniş Təchizatçının təqdim etdiyi hesab və/və ya digər müvafiq ödəniş sənədi əsasında Təchizatçının bank hesabına köçürülür.</p>
<p>3.3. Əlavə funksiyaların hazırlanması, fərdi dəyişikliklər, inteqrasiyalar və bu Müqavilənin pulsuz texniki dəstək çərçivəsinə daxil olmayan xidmətlər ayrıca qiymətləndirilir və Tərəflərin razılığı əsasında ödənişli həyata keçirilir.</p>

<h2>4. QURAŞDIRILMA VƏ TƏHVİL</h2>
<p>4.1. Təchizatçı ödəniş və zəruri giriş/texniki şərait təmin edildikdən sonra proqramı Müştərinin uyğun kompüter(lər)ində quraşdırır və ilkin sazlamanı həyata keçirir.</p>
<p>4.2. Quraşdırmadan sonra proqramın əsas funksiyalarının işləməsi Müştərinin nümayəndəsi ilə yoxlanılır.</p>
<p>4.3. Proqramın istifadəsi üçün uyğun Chrome brauzeri, internet bağlantısı və new e-taxes portalına etibarlı giriş imkanının təmin edilməsi Müştərinin məsuliyyətindədir.</p>

<h2>5. TEXNİKİ DƏSTƏK</h2>
<p>5.1. Təchizatçı tərəfindən hazırlanmış proqram kodundan qaynaqlanan və proqramın bu Müqavilədə nəzərdə tutulmuş funksiyalarının düzgün işləməsinə mane olan texniki xətalar Təchizatçı tərəfindən əlavə ödəniş tələb edilmədən araşdırılır və aradan qaldırılır.</p>
<p>5.2. Müştərinin kompüteri, əməliyyat sistemi, brauzer sazlamaları və əlavələri, antivirus və təhlükəsizlik siyasətləri, internet bağlantısı, istifadəçi hesabı, giriş məlumatları, elektron imza vasitələri, portal hesabının məhdudiyyətləri və ya Müştərinin hərəkətlərindən qaynaqlanan problemlər proqram təminatının xətası hesab edilmir. Belə hallarda Təchizatçı göstərilən texniki xidmət üçün əlavə ödəniş tələb edə bilər.</p>
<p>5.3. Müştərinin yeni funksiya, əlavə hesabat, inteqrasiya, fərqli iş axını və ya mövcud funksiyanın dəyişdirilməsi ilə bağlı tələbləri ayrıca iş hesab olunur və ayrıca qiymətləndirilir.</p>

<h2>6. NEW E-TAXES PORTALI VƏ ÜÇÜNCÜ TƏRƏF SİSTEMLƏRİ</h2>
<p>6.1. Proqram new e-taxes portalı ilə qarşılıqlı işləyən köməkçi proqramdır. Portal və onun infrastrukturu Təchizatçıya məxsus deyil və Təchizatçının nəzarətində deyil.</p>
<p>6.2. new e-taxes portalının interfeysində, məlumat mübadiləsi mexanizmlərində, API-lərində, autentifikasiya və təhlükəsizlik mexanizmlərində, domen/ünvan strukturunda və ya digər texniki komponentlərində üçüncü tərəf tərəfindən edilən dəyişikliklər proqramın fəaliyyətinə qismən və ya tam təsir göstərə bilər.</p>
<p>6.3. 6.2-ci bənddə göstərilən səbəblərdən yaranan dayanma və ya uyğunsuzluq proqram təminatının öz xətası hesab edilmir.</p>
<p>6.4. Portal dəyişikliyindən sonra proqramın uyğunlaşdırılması tələb olunduqda Təchizatçı tələb olunan işin həcmini və texniki imkanları qiymətləndirir. Təchizatçı uyğunlaşdırmanı əlavə ödənişsiz həyata keçirmək hüququnu özündə saxlayır. Əhəmiyyətli dəyişiklik, proqramın yenidən işlənməsi, yeni inteqrasiya və ya əlavə proqramlaşdırma tələb olunduqda xidmətin dəyəri Tərəflər arasında ayrıca razılaşdırılır.</p>
<p>6.5. Təchizatçı new e-taxes portalının fasiləsiz işləməsinə, üçüncü tərəf xidmətlərinin mövcudluğuna və ya həmin sistemlərdə gələcəkdə ediləcək dəyişikliklərin proqramla daim uyğun qalacağına zəmanət vermir.</p>

<h2>7. MƏLUMATLARIN TƏHLÜKƏSİZLİYİ VƏ İSTİFADƏÇİ MƏSULİYYƏTİ</h2>
<p>7.1. Müştəri new e-taxes portalına giriş məlumatlarının və digər autentifikasiya vasitələrinin məxfiliyinə görə məsuliyyət daşıyır.</p>
<p>7.2. Müştəri proqram vasitəsilə yüklənmiş sənədlərin saxlanması, ehtiyat nüsxələrinin yaradılması və onlara giriş hüquqlarının idarə edilməsini özü təmin edir.</p>
<p>7.3. Təchizatçıya texniki dəstək məqsədilə uzaqdan giriş verildikdə bu giriş yalnız razılaşdırılmış texniki işlərin görülməsi üçün istifadə olunur.</p>

<h2>8. ƏQLİ MÜLKİYYƏT</h2>
<p>8.1. Proqram təminatı, onun proqram kodu, interfeys elementləri, texniki həlləri və Təchizatçı tərəfindən yaradılmış digər materiallar müəlliflik hüququ və digər əqli mülkiyyət hüquqları ilə qorunur.</p>
<p>8.2. Müştəriyə yalnız bu Müqavilədə göstərilən həcmdə istifadə hüququ verilir. Müqavilə proqram təminatının mülkiyyətinin və ya mənbə kodunun Müştəriyə ötürülməsi kimi şərh edilə bilməz.</p>
<p>8.3. Müştəri proqramın icazəsiz surətini çıxarmamalı, yaymamalı və üçüncü şəxslərə istifadəyə verməməlidir.</p>

<h2>9. MƏSULİYYƏTİN HÜDUDLARI</h2>
<p>9.1. Tərəflər bu Müqavilə üzrə öhdəliklərini Azərbaycan Respublikasının qanunvericiliyinə və bu Müqavilənin şərtlərinə uyğun yerinə yetirirlər.</p>
<p>9.2. Təchizatçı üçüncü tərəf portallarının, internet provayderlərinin, brauzer istehsalçılarının, əməliyyat sistemlərinin və digər xarici sistemlərin fəaliyyətindən yaranan fasilələrə görə məsuliyyət daşımır.</p>
<p>9.3. Müştəri proqramdan istifadə zamanı əməliyyatların nəticələrini, yüklənmiş sənədləri və tarix aralığı/şirkət seçimi kimi daxil etdiyi parametrləri yoxlamağa məsuldur. Proqram Müştərinin mühasibat və vergi nəzarətini əvəz etmir.</p>

<h2>10. MÜDDƏT VƏ XİTAM</h2>
<p>10.1. Müqavilə Tərəflər tərəfindən imzalandığı tarixdən qüvvəyə minir.</p>
<p>10.2. Müştəriyə verilən lisenziya, bu Müqavilədə nəzərdə tutulmuş şərt və məhdudiyyətlər daxilində, lisenziyanın aktivləşdirildiyi maksimum {{deal.computers}} kompüterdə müddətsiz istifadə hüququ verir. Lisenziya üçün ödəniş birdəfəlikdir. Bu istifadə hüququ Təchizatçının üçüncü tərəf sistemlərində gələcəkdə edilən dəyişikliklərə görə proqramı limitsiz və ödənişsiz yenidən hazırlaması və ya uyğunlaşdırması öhdəliyi yaratmır.</p>
<p>10.3. Müştəri 2.2 və 2.3-cü bəndləri əhəmiyyətli şəkildə pozduqda Təchizatçı pozuntunun aradan qaldırılmasını tələb edə və qanunvericiliyin imkan verdiyi həddə lisenziyanın istifadəsini məhdudlaşdıra bilər.</p>
<p>10.4. Müqaviləyə dəyişiklik və əlavələr Tərəflərin yazılı razılığı ilə edilir.</p>

<h2>11. MÜBAHİSƏLƏRİN HƏLLİ</h2>
<p>11.1. Mübahisələr ilk növbədə danışıqlar yolu ilə həll edilir.</p>
<p>11.2. Razılıq əldə edilmədikdə mübahisə Azərbaycan Respublikasının qanunvericiliyinə uyğun qaydada həll edilir.</p>

<h2>12. YEKUN MÜDDƏALAR</h2>
<p>12.1. Müqavilə eyni hüquqi qüvvəyə malik 2 (iki) nüsxədə tərtib edilir, hər Tərəfə 1 (bir) nüsxə verilir.</p>
<p>12.2. Tərəflərin rekvizitlərində dəyişiklik olduqda digər Tərəfə məlumat verilməlidir.</p>
<p>12.3. Bu Müqavilənin ayrılmaz hissəsi kimi, zərurət olduqda hesab, təhvil-təslim aktı, texniki tapşırıq və ya əlavə razılaşma tərtib edilə bilər.</p>

<h2>13. TƏRƏFLƏRİN REKVİZİTLƏRİ VƏ İMZALARI</h2>
<table class="sig"><tr>
<td><b>TƏCHİZATÇI</b><br>{{company.name}}<br>VÖEN: {{company.voen}}<br>Ünvan: {{company.address}}<br>Bank: {{company.bank}}<br>Kod: {{company.bankCode}}<br>H/h: {{company.iban}}<br>SWIFT: {{company.swift}}<br>Direktor: {{company.director}}<br>E-mail: {{company.email}}<br>Tel.: {{company.phone}}<br><br>İmza: ____________________<br>M.Y.</td>
<td><b>MÜŞTƏRİ</b><br>{{customer.name}}<br>VÖEN: {{customer.voen}}<br>Ünvan: {{customer.address}}<br>Bank: {{customer.bank}}<br>Kod: {{customer.bankCode}}<br>H/h: {{customer.iban}}<br>SWIFT: {{customer.swift}}<br>Direktor: {{customer.director}}<br>E-mail: {{customer.email}}<br>Tel.: {{customer.phone}}<br><br>İmza: ____________________<br>M.Y.</td>
</tr></table>
</div></body></html>');

insert into template (code, title, is_default, html) values ('PROTOCOL', 'Qiymət razılaşdırma protokolu', true, '<!DOCTYPE html>
<html lang="az"><head><meta charset="utf-8"><title>Qiymət razılaşdırma protokolu</title>
<style>
@page { size: A4; margin: 18mm 16mm; }
body { font-family: "Times New Roman", serif; font-size: 12pt; line-height: 1.4; color: #000; margin: 0; }
.doc { max-width: 178mm; margin: 0 auto; padding: 8mm 0; }
h1 { text-align: center; font-size: 14pt; margin: 0 0 4px; }
.c { text-align: center; }
.row { display: flex; justify-content: space-between; margin: 10px 0; }
p { margin: 6px 0; text-align: justify; }
table.t { width: 100%; border-collapse: collapse; margin: 12px 0; }
table.t th, table.t td { border: 1px solid #000; padding: 5px 8px; text-align: left; }
table.sig { width: 100%; border-collapse: collapse; margin-top: 30px; page-break-inside: avoid; }
table.sig td { width: 50%; vertical-align: top; padding: 4px 8px 4px 0; }
</style></head><body><div class="doc">
<h1>QİYMƏT RAZILAŞDIRMA PROTOKOLU</h1>
<p class="c">Müqavilə № {{deal.contractNo}} üzrə</p>
<div class="row"><span>Bakı şəhəri</span><span>{{deal.protocolDate}}</span></div>
<p>{{company.name}} (VÖEN: {{company.voen}}) (bundan sonra – “Təchizatçı”), direktor {{company.director}} şəxsində, və {{customer.name}} (VÖEN: {{customer.voen}}) (bundan sonra – “Müştəri”), direktor {{customer.director}} şəxsində, {{deal.contractDate}} tarixli müqavilə çərçivəsində aşağıdakı qiymət barədə razılığa gəldilər.</p>
<table class="t">
<tr><th>Məhsul / xidmət</th><th>Kompüter sayı</th><th>Qiymət (AZN)</th></tr>
<tr><td>{{deal.product}}</td><td>{{deal.computers}}</td><td>{{deal.price}}</td></tr>
</table>
<p>Razılaşdırılmış ümumi qiymət: <b>{{deal.price}} AZN</b> ({{deal.priceWords}}).</p>
<p>Protokol iki nüsxədə tərtib edilmişdir, hər Tərəfə bir nüsxə verilir və imzalandığı tarixdən qüvvəyə minir.</p>
<table class="sig"><tr>
<td><b>TƏCHİZATÇI</b><br>{{company.name}}<br><br>İmza: ____________________<br>M.Y.</td>
<td><b>MÜŞTƏRİ</b><br>{{customer.name}}<br><br>İmza: ____________________<br>M.Y.</td>
</tr></table>
</div></body></html>');

insert into template (code, title, is_default, html) values ('ACT', 'Təhvil-təslim aktı', true, '<!DOCTYPE html>
<html lang="az"><head><meta charset="utf-8"><title>Təhvil-təslim aktı № {{deal.actNo}}</title>
<style>
@page { size: A4; margin: 18mm 16mm; }
body { font-family: "Times New Roman", serif; font-size: 12pt; line-height: 1.4; color: #000; margin: 0; }
.doc { max-width: 178mm; margin: 0 auto; padding: 8mm 0; }
h1 { text-align: center; font-size: 14pt; margin: 0 0 4px; }
.c { text-align: center; }
.row { display: flex; justify-content: space-between; margin: 10px 0; }
p { margin: 6px 0; text-align: justify; }
table.t { width: 100%; border-collapse: collapse; margin: 12px 0; }
table.t th, table.t td { border: 1px solid #000; padding: 5px 8px; text-align: left; }
table.sig { width: 100%; border-collapse: collapse; margin-top: 30px; page-break-inside: avoid; }
table.sig td { width: 50%; vertical-align: top; padding: 4px 8px 4px 0; }
</style></head><body><div class="doc">
<h1>TƏHVİL-TƏSLİM AKTI</h1>
<p class="c">№ {{deal.actNo}}</p>
<div class="row"><span>Bakı şəhəri</span><span>{{deal.actDate}}</span></div>
<p>Müqavilə № {{deal.contractNo}}, {{deal.contractDate}}.</p>
<p>Təchizatçı: {{company.name}}, VÖEN: {{company.voen}}, direktor: {{company.director}}, ünvan: {{company.address}}, e-mail: {{company.email}}, tel.: {{company.phone}}.</p>
<p>Sifarişçi: {{customer.name}}, VÖEN: {{customer.voen}}, direktor: {{customer.director}}.</p>
<table class="t">
<tr><th>№</th><th>Mal / proqram təminatının / xidmətin təsviri</th><th>Ölçü vahidi</th><th>Miqdar</th><th>Məbləğ (AZN)</th></tr>
<tr><td>1</td><td>“{{deal.product}}” proqram təminatından istifadə hüququ (lisenziya), quraşdırılma və aktivləşdirmə</td><td>ədəd</td><td>{{deal.computers}}</td><td>{{deal.price}}</td></tr>
</table>
<p>Cəmi: <b>{{deal.price}} AZN</b> ({{deal.priceWords}}).</p>
<p>Sifarişçi yuxarıda göstərilən proqram təminatı / xidmətin təqdim edildiyini, proqram təminatının quraşdırıldığını və aktivləşdirildiyini təsdiq edir. Tərəflərin təhvil-təslim üzrə bir-birinə qarşı iradı yoxdur.</p>
<table class="sig"><tr>
<td><b>Sifarişçi adından</b><br>{{customer.name}}<br><br>İmza / M.Y. ____________________<br>( ad soyad )<br>( vəzifə )</td>
<td><b>Təchizatçı adından</b><br>{{company.name}}<br><br>İmza / M.Y. ____________________<br>( ad soyad )<br>( vəzifə )</td>
</tr></table>
</div></body></html>');
