package az.innotex.sade;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Satışın öz sənədləri: şablondan doldurulur, sonra istifadəçi redaktə edir (şablon dəyişmir) */
@Service
public class DocService {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*}}");
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String BLANK = "__________";

    private final R.DealDocs docs;
    private final R.Templates templates;
    private final R.Deals deals;
    private final R.Customers customers;
    private final R.Products products;
    private final SettingsService settings;
    private final FileService fileSvc;
    private final Numbering numbering;

    public DocService(R.DealDocs docs, R.Templates templates, R.Deals deals, R.Customers customers, R.Products products, SettingsService settings, FileService fileSvc, Numbering numbering) {
        this.numbering = numbering;
        this.fileSvc = fileSvc;
        this.docs = docs;
        this.templates = templates;
        this.deals = deals;
        this.customers = customers;
        this.products = products;
        this.settings = settings;
    }

    /** Yalnız HTML üçün təhlükəli simvollar (Azərbaycan hərfləri olduğu kimi qalır) */
    static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }

    private static String date(LocalDate d) { return d == null ? null : d.format(DMY); }

    /** Şirkət + müştəri + satış məlumatı üzrə placeholder xəritəsi */
    Map<String, String> values(E.Deal d) {
        Map<String, String> v = new HashMap<>();
        E.Settings s = settings.entity();
        v.put("company.name", s.companyName); v.put("company.voen", s.voen); v.put("company.address", s.address);
        v.put("company.director", s.director); v.put("company.bank", s.bank); v.put("company.iban", s.iban);
        v.put("company.bankCode", s.bankCode); v.put("company.bankVoen", s.bankVoen); v.put("company.correspondentAccount", s.correspondentAccount); v.put("company.swift", s.swift); v.put("company.phone", s.phone); v.put("company.email", s.email);
        E.Customer c = customers.findById(d.customerId).orElseThrow(() -> ApiException.notFound("Kontragent tapılmadı"));
        v.put("customer.name", c.name); v.put("customer.voen", c.voen); v.put("customer.address", c.address);
        v.put("customer.director", c.director); v.put("customer.bank", c.bank); v.put("customer.iban", c.iban);
        v.put("customer.bankCode", c.bankCode); v.put("customer.bankVoen", c.bankVoen); v.put("customer.correspondentAccount", c.correspondentAccount); v.put("customer.swift", c.swift); v.put("customer.phone", c.phone); v.put("customer.email", c.email);
        v.put("deal.contractNo", d.contractNo); v.put("deal.contractDate", date(d.contractDate));
        v.put("deal.price", Money.s(d.price)); v.put("deal.priceWords", Words.amount(d.price));
        v.put("deal.computers", String.valueOf(d.computers)); v.put("deal.quantity", String.valueOf(d.quantity));
        v.put("deal.unitPrice", Money.s(d.price.divide(java.math.BigDecimal.valueOf(d.quantity), 2, java.math.RoundingMode.HALF_UP)));
        v.put("deal.product", d.product); v.put("deal.description", d.description);
        v.put("deal.actNo", d.actNo); v.put("deal.actDate", date(d.actDate)); v.put("deal.protocolDate", date(d.protocolDate));
        v.put("deal.advanceInvoiceNo", d.advanceInvoiceNo); v.put("deal.advanceInvoiceDate", date(d.advanceInvoiceDate));
        v.put("deal.invoiceNo", d.invoiceNo); v.put("deal.invoiceDate", date(d.invoiceDate));
        E.Product pr = d.productId == null ? null : products.findById(d.productId).orElse(null);
        v.put("product.name", d.product); v.put("product.code", pr == null ? null : pr.code);
        v.put("product.unit", pr == null ? null : pr.unit); v.put("product.description", pr == null ? null : pr.description);
        v.put("today", date(LocalDate.now()));
        return v;
    }

    /** {{açar}} → dəyər (HTML-escape); boş dəyər çap üçün xətt olur, naməlum açar boş qalır */
    static String fill(String html, Map<String, String> values) {
        Matcher m = PLACEHOLDER.matcher(html);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String val = values.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(val == null || val.isBlank() ? BLANK : esc(val)));
        }
        m.appendTail(out);
        return out.toString();
    }

    private E.Deal deal(Long id) { return deals.findById(id).orElseThrow(() -> ApiException.notFound("Satış tapılmadı")); }
    private static void noRetail(E.Deal d) { if ("RETAIL".equals(d.paymentTerms)) throw ApiException.bad("Fiziki şəxs satışında müqavilə və akt sənədləri yoxdur"); }

    private E.DealDocument doc(Long id) { return docs.findById(id).orElseThrow(() -> ApiException.notFound("Sənəd tapılmadı")); }

    private static Dto.DealDocRes toDto(E.DealDocument d) {
        return new Dto.DealDocRes(d.id, d.dealId, d.code, d.templateId, d.title, d.html, d.format, d.fileId, d.previousFileId != null, d.createdAt.toString(), d.updatedAt.toString());
    }

    private E.Template pick(String code, Long templateId) {
        if (templateId != null) {
            E.Template t = settings.template(templateId);
            if (!t.code.equals(code)) throw ApiException.bad("Şablon seçilmiş sənəd növünə uyğun deyil");
            return t;
        }
        List<E.Template> all = templates.findByCodeOrderByIdAsc(code);
        return all.stream().filter(t -> t.isDefault).findFirst().or(() -> all.stream().findFirst())
                .orElseThrow(() -> ApiException.notFound("Bu növ üçün şablon yoxdur"));
    }

    public List<Dto.DealDocRes> list(Long dealId) {
        deal(dealId);
        return docs.findByDealIdOrderByIdAsc(dealId).stream().map(DocService::toDto).toList();
    }

    @Transactional
    public Dto.DealDocRes create(Long dealId, Dto.DealDocCreateReq r) {
        E.Deal d = deal(dealId);
        noRetail(d);
        String code = r.code() == null ? "" : r.code().toUpperCase();
        if (!SettingsService.TEMPLATE_CODES.contains(code)) throw ApiException.bad("Sənəd növü CONTRACT, PROTOCOL və ya ACT olmalıdır");
        E.Template t = pick(code, r.templateId());
        if ("ACT".equals(code)) {   // akt şablondan yaradılanda boş akt nömrəsi/tarixi dolur
            if (d.actDate == null) d.actDate = LocalDate.now();
            if (d.actNo == null || d.actNo.isBlank()) d.actNo = numbering.nextActNo(d.actDate.getYear());
            deals.save(d);
        }
        E.DealDocument x = new E.DealDocument();
        x.dealId = dealId;
        x.code = code;
        x.templateId = t.id;
        x.title = t.title;
        generate(x, t, d);
        x.createdAt = LocalDateTime.now();
        x.updatedAt = x.createdAt;
        return toDto(docs.save(x));
    }

    private static String safe(String s) { return s == null ? "" : s.replaceAll("[\\\\/:*?\"<>|\r\n]", " ").replaceAll("\\s+", " ").trim(); }

    /** Şablondan məzmun yaradır: HTML üçün mətn, DOCX üçün şablon faylının doldurulmuş surəti (şablon dəyişmir) */
    private void generate(E.DealDocument x, E.Template t, E.Deal d) {
        x.format = t.format == null ? "HTML" : t.format;
        if (x.format.equals("DOCX")) {
            if (t.fileId == null) throw ApiException.bad("Word şablonunun faylı yoxdur");
            byte[] filled = Docx.fill(fileSvc.bytes(t.fileId), values(d));
            E.StoredFile f = fileSvc.storeBytes(filled, safe(t.title) + ".docx");
            // bir addım geri üçün əvvəlki fayl saxlanır, ondan da əvvəlkilər silinir
            if (x.fileId != null) { fileSvc.delete(x.previousFileId); x.previousFileId = x.fileId; }
            x.fileId = f.id;
            x.html = null;
        } else {
            x.html = fill(t.html, values(d));
            x.fileId = null;
        }
    }

    /** Fayl əsaslı sənəd (DOCX və ya PDF) */
    private E.DealDocument docxDoc(Long id) {
        E.DealDocument x = doc(id);
        if ("HTML".equals(x.format) || x.fileId == null) throw ApiException.bad("Bu sənəd fayl əsaslı deyil");
        return x;
    }

    private static final java.util.Set<String> UPLOAD_CODES = java.util.Set.of("CONTRACT", "PROTOCOL", "ACT");

    /** Yoxlayır: DOCX və ya PDF; formatı qaytarır */
    private static String checkFile(org.springframework.web.multipart.MultipartFile file) {
        String n = file == null ? null : file.getOriginalFilename();
        if (file == null || file.isEmpty() || n == null) throw ApiException.bad("Fayl seçilməyib");
        String low = n.toLowerCase();
        byte[] b;
        try { b = file.getBytes(); } catch (java.io.IOException e) { throw ApiException.bad("Fayl oxunmadı"); }
        if (low.endsWith(".docx")) { Docx.validate(b); return "DOCX"; }
        if (low.endsWith(".pdf")) {
            if (b.length < 5 || b[0] != '%' || b[1] != 'P' || b[2] != 'D' || b[3] != 'F') throw ApiException.bad("Fayl düzgün PDF deyil");
            return "PDF";
        }
        throw ApiException.bad("Yalnız .docx və ya .pdf fayl yüklənə bilər");
    }

    /** Hazır (şablonsuz) sənəd yükləyir: DOCX və ya PDF */
    @Transactional
    public Dto.DealDocRes uploadNew(Long dealId, String code, String title, org.springframework.web.multipart.MultipartFile file) {
        noRetail(deal(dealId));
        String c = code == null ? "" : code.trim().toUpperCase();
        if (!UPLOAD_CODES.contains(c)) throw ApiException.bad("Sənəd növü CONTRACT, PROTOCOL və ya ACT olmalıdır");
        String format = checkFile(file);
        Dto.FileRes f = fileSvc.store(file);
        String orig = file.getOriginalFilename().replace('\\', '/');
        orig = orig.substring(orig.lastIndexOf('/') + 1);
        int dot = orig.lastIndexOf('.');
        E.DealDocument x = new E.DealDocument();
        x.dealId = dealId;
        x.code = c;
        x.templateId = null;
        x.title = title != null && !title.isBlank() ? title.trim() : (dot > 0 ? orig.substring(0, dot) : orig);
        x.format = format;
        x.fileId = f.id();
        x.createdAt = LocalDateTime.now();
        x.updatedAt = x.createdAt;
        return toDto(docs.save(x));
    }

    /** Yükləmə adı: «Müqavilə INX-2026-PS-001 - Müştəri.docx» */
    public String docxName(Long id) {
        E.DealDocument x = doc(id);
        E.Deal d = deal(x.dealId);
        String kind = switch (x.code) { case "CONTRACT" -> "Müqavilə"; case "PROTOCOL" -> "Protokol"; default -> "Akt"; };
        String no = "ACT".equals(x.code) && d.actNo != null && !d.actNo.isBlank() ? d.actNo : d.contractNo;
        String cust = customers.findById(d.customerId).map(c -> c.name).orElse("");
        // eyni mərhələnin əlavə sənədi (məs. müqaviləyə əlavə) öz adı ilə endirilir
        boolean extra = docs.findByDealIdOrderByIdAsc(d.id).stream().filter(o -> o.code.equals(x.code)).findFirst().map(o -> !o.id.equals(x.id)).orElse(false);
        if (extra) return safe(x.title + " " + (no == null ? "" : no) + " - " + cust) + ("PDF".equals(x.format) ? ".pdf" : ".docx");
        return safe(kind + " " + (no == null ? "" : no) + " - " + cust) +("PDF".equals(x.format) ? ".pdf" : ".docx");
    }

    public String contentType(Long id) {
        return "PDF".equals(doc(id).format) ? "application/pdf" : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    }

    public byte[] docxBytes(Long id) { return fileSvc.bytes(docxDoc(id).fileId); }

    /** İstifadəçinin redaktə etdiyi faylla əvəz edir; əvvəlki fayl bir addım geri üçün saxlanır */
    @Transactional
    public Dto.DealDocRes upload(Long id, org.springframework.web.multipart.MultipartFile file) {
        E.DealDocument x = docxDoc(id);
        String fmt = checkFile(file);
        if (!fmt.equals(x.format)) throw ApiException.bad("PDF".equals(x.format) ? "Fayl .pdf olmalıdır" : "Fayl .docx olmalıdır");
        Dto.FileRes f = fileSvc.store(file);
        fileSvc.delete(x.previousFileId);
        x.previousFileId = x.fileId;
        x.fileId = f.id();
        x.updatedAt = LocalDateTime.now();
        return toDto(docs.save(x));
    }

    /** Bir addım geri: cari və əvvəlki fayl yerini dəyişir */
    @Transactional
    public Dto.DealDocRes revert(Long id) {
        E.DealDocument x = docxDoc(id);
        if (x.previousFileId == null) throw ApiException.conflict("evvelki_yoxdur", "Əvvəlki versiya yoxdur");
        Long cur = x.fileId;
        x.fileId = x.previousFileId;
        x.previousFileId = cur;
        x.updatedAt = LocalDateTime.now();
        return toDto(docs.save(x));
    }

    public Dto.DealDocRes get(Long id) { return toDto(doc(id)); }

    @Transactional
    public Dto.DealDocRes update(Long id, Dto.DealDocReq r) {
        E.DealDocument x = doc(id);
        boolean docx = !"HTML".equals(x.format);
        // html göndərilməyəndə (yalnız ad dəyişir) mətn qalır
        if (!docx && r.html() != null && r.html().isBlank()) throw ApiException.bad("Sənədin mətni boş ola bilməz");
        if (r.title() != null && r.title().isBlank()) throw ApiException.bad("Sənədin adı boş ola bilməz");
        if (r.title() != null) x.title = r.title().trim();
        if (!docx && r.html() != null) x.html = r.html();
        x.updatedAt = LocalDateTime.now();
        return toDto(docs.save(x));
    }

    @Transactional
    public void delete(Long id) {
        E.DealDocument x = doc(id);
        docs.delete(x);
        fileSvc.delete(x.fileId);
        fileSvc.delete(x.previousFileId);
    }

    /** Şablondan yenidən doldurur: əl dəyişiklikləri itir */
    @Transactional
    public Dto.DealDocRes refill(Long id) {
        E.DealDocument x = doc(id);
        if (x.templateId == null) throw ApiException.bad("Şablondan yaradılmayıb");
        E.Template t = x.templateId != null ? templates.findById(x.templateId).orElse(null) : null;
        if (t == null) t = pick(x.code, null);
        x.templateId = t.id;
        generate(x, t, deal(x.dealId));
        x.updatedAt = LocalDateTime.now();
        return toDto(docs.save(x));
    }

    /** A4 çap HTML: tam sənəd olduğu kimi, fraqment isə A4 qabığına bükülür */
    public String print(Long id) {
        E.DealDocument x = doc(id);
        if (x.html == null) throw ApiException.bad("Word sənədi üçün çap HTML-i yoxdur");
        String h = x.html;
        if (h.toLowerCase().contains("<html")) return h;
        return "<!DOCTYPE html><html lang=\"az\"><head><meta charset=\"utf-8\"><title>" + esc(x.title) + "</title><style>"
                + "@page{size:A4;margin:18mm 16mm}body{font-family:\"Times New Roman\",serif;font-size:12pt;line-height:1.35;color:#000;margin:0}"
                + ".doc{max-width:178mm;margin:0 auto;padding:8mm 0}p{margin:3px 0;text-align:justify}h1{text-align:center;font-size:14pt}h2{font-size:12pt}"
                + "table{border-collapse:collapse}td,th{padding:4px 8px;vertical-align:top}"
                + "</style></head><body><div class=\"doc\">" + h + "</div></body></html>";
    }
}
