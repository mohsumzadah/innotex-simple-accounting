package az.innotex.sade;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Ayarlar: rekvizitlər, vergi dərəcəsi, maaş dərəcələri, iş günü cədvəli, şablonlar */
@Service
public class SettingsService {
    public static final Set<String> TEMPLATE_CODES = Set.of("CONTRACT", "PROTOCOL", "ACT");
    private static final String MONTH = "\\d{4}-(0[1-9]|1[0-2])";

    private final R.SettingsRepo settings;
    private final R.Rates rates;
    private final R.Calendar calendar;
    private final R.Templates templates;
    private final FileService fileSvc;

    public SettingsService(R.SettingsRepo settings, R.Rates rates, R.Calendar calendar, R.Templates templates, FileService fileSvc) {
        this.fileSvc = fileSvc;
        this.settings = settings;
        this.rates = rates;
        this.calendar = calendar;
        this.templates = templates;
    }

    public E.Settings entity() { return settings.findById(1L).orElseThrow(() -> ApiException.notFound("Ayarlar tapılmadı")); }

    private static String n(String s) { return s == null ? "" : s.trim(); }

    public Dto.SettingsRes get() {
        E.Settings s = entity();
        return new Dto.SettingsRes(s.companyName, s.voen, s.address, s.director, s.bank, s.iban, s.bankCode, s.bankVoen, s.swift, s.correspondentAccount, s.phone, s.email,
                Money.rate(s.profitTaxRate), s.taxMethod == null ? "ACCRUAL" : s.taxMethod);
    }

    @Transactional
    public Dto.SettingsRes put(Dto.SettingsReq r) {
        E.Settings s = entity();
        if (r.profitTaxRate() != null) {
            if (r.profitTaxRate().signum() < 0 || r.profitTaxRate().compareTo(Money.HUNDRED) > 0)
                throw ApiException.bad("Mənfəət vergisi dərəcəsi 0 ilə 100 arasında olmalıdır");
            s.profitTaxRate = r.profitTaxRate();
        }
        s.companyName = n(r.companyName()); s.voen = n(r.voen()); s.address = n(r.address()); s.director = n(r.director());
        s.bank = n(r.bank()); s.iban = n(r.iban()); s.bankCode = n(r.bankCode()); s.bankVoen = n(r.bankVoen()); s.swift = n(r.swift()); s.correspondentAccount = n(r.correspondentAccount());
        s.phone = n(r.phone()); s.email = n(r.email());
        settings.save(s);
        return get();
    }

    // ---- maaş dərəcələri ----
    static Dto.RateRes toDto(E.PayrollRate e) {
        return new Dto.RateRes(e.id, e.validFrom, Money.rate(e.dsmfLimit), Money.rate(e.dsmfEmpLow), Money.rate(e.dsmfEmpHigh),
                Money.rate(e.dsmfErLow), Money.rate(e.dsmfErHigh), Money.rate(e.unempEmp), Money.rate(e.unempEr),
                Money.rate(e.medLimit), Money.rate(e.medLow), Money.rate(e.medHigh), Money.rate(e.incomeLimit),
                Money.rate(e.incomeExempt), Money.rate(e.incomeLow), Money.rate(e.incomeHigh));
    }

    private E.PayrollRate apply(E.PayrollRate e, Dto.RateReq r) {
        if (r.validFrom() == null || !r.validFrom().matches(MONTH)) throw ApiException.bad("Başlanğıc ayı YYYY-MM formatında olmalıdır");
        BigDecimal[] all = {r.dsmfLimit(), r.dsmfEmpLow(), r.dsmfEmpHigh(), r.dsmfErLow(), r.dsmfErHigh(), r.unempEmp(), r.unempEr(),
                r.medLimit(), r.medLow(), r.medHigh(), r.incomeLimit(), r.incomeExempt(), r.incomeLow(), r.incomeHigh()};
        for (BigDecimal b : all) if (b == null || b.signum() < 0) throw ApiException.bad("Bütün dərəcə və hədlər doldurulmalı və mənfi olmamalıdır");
        e.validFrom = r.validFrom();
        e.dsmfLimit = r.dsmfLimit(); e.dsmfEmpLow = r.dsmfEmpLow(); e.dsmfEmpHigh = r.dsmfEmpHigh();
        e.dsmfErLow = r.dsmfErLow(); e.dsmfErHigh = r.dsmfErHigh(); e.unempEmp = r.unempEmp(); e.unempEr = r.unempEr();
        e.medLimit = r.medLimit(); e.medLow = r.medLow(); e.medHigh = r.medHigh();
        e.incomeLimit = r.incomeLimit(); e.incomeExempt = r.incomeExempt(); e.incomeLow = r.incomeLow(); e.incomeHigh = r.incomeHigh();
        return rates.save(e);
    }

    public List<Dto.RateRes> listRates() { return rates.findAllByOrderByValidFromAscIdAsc().stream().map(SettingsService::toDto).toList(); }

    @Transactional
    public Dto.RateRes createRate(Dto.RateReq r) { return toDto(apply(new E.PayrollRate(), r)); }

    @Transactional
    public Dto.RateRes updateRate(Long id, Dto.RateReq r) {
        return toDto(apply(rates.findById(id).orElseThrow(() -> ApiException.notFound("Dərəcə tapılmadı")), r));
    }

    @Transactional
    public void deleteRate(Long id) {
        E.PayrollRate e = rates.findById(id).orElseThrow(() -> ApiException.notFound("Dərəcə tapılmadı"));
        if (rates.count() <= 1) throw ApiException.conflict("son_derece", "Son maaş dərəcəsi silinə bilməz");
        rates.delete(e);
    }

    /** Ay üçün qüvvədə olan dərəcə: validFrom <= ay olanların ən sonuncusu */
    public E.PayrollRate rateFor(String month) {
        E.PayrollRate found = null;
        for (E.PayrollRate r : rates.findAllByOrderByValidFromAscIdAsc()) if (r.validFrom.compareTo(month) <= 0) found = r;
        if (found == null) throw ApiException.conflict("derece_yoxdur", "Bu ay üçün maaş dərəcəsi yoxdur: " + Xlsx.my(month));
        return found;
    }

    // ---- iş günü cədvəli ----
    public List<Dto.CalendarItem> calendar(int year) {
        List<Dto.CalendarItem> out = new ArrayList<>();
        Map<String, Integer> byMonth = new HashMap<>();
        calendar.findByPeriodStartingWith(year + "-").forEach(w -> byMonth.put(w.period, w.days));
        for (int m = 1; m <= 12; m++) {
            String p = String.format("%d-%02d", year, m);
            out.add(new Dto.CalendarItem(p, byMonth.getOrDefault(p, 0)));
        }
        return out;
    }

    @Transactional
    public List<Dto.CalendarItem> putCalendar(int year, List<Dto.CalendarItem> items) {
        if (items == null) throw ApiException.bad("Cədvəl boşdur");
        for (Dto.CalendarItem i : items) {
            if (i.month() == null || !i.month().matches(year + "-(0[1-9]|1[0-2])")) throw ApiException.bad("Ay " + year + "-MM formatında olmalıdır");
            if (i.days() < 0 || i.days() > 31) throw ApiException.bad("İş günü sayı 0 ilə 31 arasında olmalıdır");
            E.WorkCalendar w = calendar.findById(i.month()).orElseGet(E.WorkCalendar::new);
            w.period = i.month();
            w.days = i.days();
            calendar.save(w);
        }
        return calendar(year);
    }

    public int normDays(String month) {
        return calendar.findById(month).map(w -> w.days).orElse(0);
    }

    // ---- şablonlar (hər code üçün bir neçə, biri default) ----
    static Dto.TemplateRes toDto(E.Template t) { return new Dto.TemplateRes(t.id, t.code, t.title, t.format, t.fileId, t.html, t.isDefault); }

    public E.Template template(Long id) {
        return templates.findById(id).orElseThrow(() -> ApiException.notFound("Şablon tapılmadı"));
    }

    public List<Dto.TemplateRes> listTemplates(String code) {
        List<E.Template> l = code == null || code.isBlank() ? templates.findAllByOrderByCodeAscIdAsc() : templates.findByCodeOrderByIdAsc(code.toUpperCase());
        return l.stream().map(SettingsService::toDto).toList();
    }

    public Dto.TemplateRes getTemplate(Long id) { return toDto(template(id)); }

    private void makeDefault(E.Template t) {
        for (E.Template o : templates.findByCodeOrderByIdAsc(t.code)) {
            if (!o.id.equals(t.id) && o.isDefault) { o.isDefault = false; templates.save(o); }
        }
        t.isDefault = true;
    }

    private static String fmt(String f) { return f == null || f.isBlank() ? "HTML" : f.trim().toUpperCase(); }

    /** Başlıq + format-a görə məzmun yoxlanışı; DOCX üçün fayl etibarlı .docx olmalıdır */
    private void checkBody(Dto.TemplateReq r, String format) {
        if (r.title() == null || r.title().isBlank()) throw ApiException.bad("Şablonun adı boş ola bilməz");
        if (format.equals("DOCX")) {
            if (r.fileId() == null) throw ApiException.bad("Word şablonu üçün fayl seçilməyib");
            E.StoredFile f = fileSvc.meta(r.fileId());
            if (!f.name.toLowerCase().endsWith(".docx")) throw ApiException.bad("Fayl .docx olmalıdır");
            Docx.validate(fileSvc.bytes(f.id));
        } else if (format.equals("HTML")) {
            if (r.html() == null || r.html().isBlank()) throw ApiException.bad("Şablon mətni boş ola bilməz");
        } else throw ApiException.bad("Format HTML və ya DOCX olmalıdır");
    }

    private void apply(E.Template t, Dto.TemplateReq r, String format) {
        Long oldFile = t.fileId;
        t.title = r.title().trim();
        t.format = format;
        if (format.equals("DOCX")) { t.html = null; t.fileId = r.fileId(); }
        else { t.html = r.html(); t.fileId = null; }
        if (oldFile != null && !oldFile.equals(t.fileId)) fileSvc.delete(oldFile);
    }

    @Transactional
    public Dto.TemplateRes createTemplate(Dto.TemplateReq r) {
        String code = r.code() == null ? "" : r.code().toUpperCase();
        if (!TEMPLATE_CODES.contains(code)) throw ApiException.bad("Şablon kodu CONTRACT, PROTOCOL və ya ACT olmalıdır");
        String format = fmt(r.format());
        checkBody(r, format);
        E.Template t = new E.Template();
        t.code = code;
        apply(t, r, format);
        t.isDefault = false;
        t = templates.save(t);
        if (Boolean.TRUE.equals(r.isDefault()) || templates.findByCodeOrderByIdAsc(code).size() == 1) makeDefault(t);
        return toDto(templates.save(t));
    }

    @Transactional
    public Dto.TemplateRes putTemplate(Long id, Dto.TemplateReq r) {
        E.Template t = template(id);
        String format = fmt(r.format());
        checkBody(r, format);
        apply(t, r, format);
        if (Boolean.TRUE.equals(r.isDefault())) makeDefault(t);
        return toDto(templates.save(t));
    }

    @Transactional
    public void deleteTemplate(Long id) {
        E.Template t = template(id);
        List<E.Template> same = templates.findByCodeOrderByIdAsc(t.code);
        if (same.size() <= 1) throw ApiException.conflict("son_sablon", "Bu növ üçün son şablon silinə bilməz");
        templates.delete(t);
        fileSvc.delete(t.fileId);
        if (t.isDefault) {
            E.Template next = same.stream().filter(o -> !o.id.equals(t.id)).findFirst().orElseThrow();
            next.isDefault = true;
            templates.save(next);
        }
    }
}
