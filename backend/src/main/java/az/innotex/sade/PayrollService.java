package az.innotex.sade;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** İşçilər və aylıq maaş cədvəlləri (SPEC §4). Dərəcələr payroll_rate cədvəlindən gəlir, kodda deyil. */
@Service
public class PayrollService {
    private final R.Employees employees;
    private final R.Runs runs;
    private final R.Lines lines;
    private final SettingsService settings;

    private final R.PaymentCodes codes;
    private final R.PayrollPayments paylinks;
    private final R.PayrollRunFiles runFiles;
    private final R.Movements movements;
    private final R.Accounts accounts;
    private final FileService fileSvc;

    public PayrollService(R.Employees employees, R.Runs runs, R.Lines lines, SettingsService settings, R.PaymentCodes codes,
                          R.PayrollPayments paylinks, R.PayrollRunFiles runFiles, R.Movements movements, R.Accounts accounts, FileService fileSvc) {
        this.paylinks = paylinks;
        this.runFiles = runFiles;
        this.movements = movements;
        this.accounts = accounts;
        this.fileSvc = fileSvc;
        this.codes = codes;
        this.employees = employees;
        this.runs = runs;
        this.lines = lines;
        this.settings = settings;
    }

    // ---- hesablama ----
    public record Calc(BigDecimal accrued, BigDecimal income, BigDecimal dsmfEmp, BigDecimal medEmp, BigDecimal unempEmp,
                       BigDecimal net, BigDecimal dsmfEr, BigDecimal medEr, BigDecimal unempEr, BigDecimal employerCost) {}

    /** limit-ə qədər aşağı, artığa yuxarı faiz (faizlər %-lə), nəticə 2 rəqəmə yuvarlaqlaşdırılır */
    static BigDecimal tier(BigDecimal g, BigDecimal limit, BigDecimal low, BigDecimal high) {
        BigDecimal v = g.compareTo(limit) <= 0
                ? g.multiply(low)
                : limit.multiply(low).add(g.subtract(limit).multiply(high));
        return Money.r2(v.divide(Money.HUNDRED, 6, RoundingMode.HALF_UP));
    }

    static Calc calc(E.PayrollRate r, BigDecimal gross, String workplace, int normDays, int workedDays) {
        BigDecimal g = Money.r2(gross.multiply(BigDecimal.valueOf(workedDays)).divide(BigDecimal.valueOf(normDays), 2, RoundingMode.HALF_UP));
        BigDecimal dsmfEmp = tier(g, r.dsmfLimit, r.dsmfEmpLow, r.dsmfEmpHigh);
        BigDecimal dsmfEr = tier(g, r.dsmfLimit, r.dsmfErLow, r.dsmfErHigh);
        BigDecimal med = tier(g, r.medLimit, r.medLow, r.medHigh);
        BigDecimal base = g.subtract("MAIN".equals(workplace) ? r.incomeExempt : BigDecimal.ZERO).max(BigDecimal.ZERO);
        BigDecimal income = tier(base, r.incomeLimit, r.incomeLow, r.incomeHigh);
        BigDecimal unempEmp = Money.r2(g.multiply(r.unempEmp).divide(Money.HUNDRED, 6, RoundingMode.HALF_UP));
        BigDecimal unempEr = Money.r2(g.multiply(r.unempEr).divide(Money.HUNDRED, 6, RoundingMode.HALF_UP));
        BigDecimal net = g.subtract(dsmfEmp).subtract(unempEmp).subtract(med).subtract(income);
        BigDecimal cost = g.add(dsmfEr).add(unempEr).add(med);
        return new Calc(g, income, dsmfEmp, med, unempEmp, Money.r2(net), dsmfEr, med, unempEr, Money.r2(cost));
    }

    private static void fill(E.PayrollLine l, Calc c) {
        l.accrued = c.accrued(); l.income = c.income(); l.dsmfEmp = c.dsmfEmp(); l.medEmp = c.medEmp(); l.unempEmp = c.unempEmp();
        l.net = c.net(); l.dsmfEr = c.dsmfEr(); l.medEr = c.medEr(); l.unempEr = c.unempEr(); l.employerCost = c.employerCost();
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    // ---- işçilər ----
    static String ym(java.time.LocalDate d) { return d == null ? null : java.time.YearMonth.from(d).toString(); }

    private static java.time.LocalDate parseYm(String s) {
        if (s == null || s.isBlank()) return null;
        try { return java.time.YearMonth.parse(s.trim()).atEndOfMonth(); }
        catch (java.time.format.DateTimeParseException ex) { throw ApiException.bad("Kartın bitmə tarixi YYYY-AA formatında olmalıdır"); }
    }

    private static Dto.EmployeeRes toDto(E.Employee e) {
        return new Dto.EmployeeRes(e.id, e.name, e.jobTitle, e.workplace, Money.s(e.gross), e.active, e.bankName, e.iban, e.cardNumber, e.fin, e.note, ym(e.cardExpiry));
    }

    public List<Dto.EmployeeRes> listEmployees() {
        return employees.findAll().stream().sorted(Comparator.comparing(e -> e.id)).map(PayrollService::toDto).toList();
    }

    private E.Employee apply(E.Employee e, Dto.EmployeeReq r) {
        if (r.name() == null || r.name().isBlank()) throw ApiException.bad("İşçinin adı boş ola bilməz");
        if (!"MAIN".equals(r.workplace()) && !"SECONDARY".equals(r.workplace())) throw ApiException.bad("İş yeri MAIN və ya SECONDARY olmalıdır");
        if (r.gross() == null || r.gross().signum() < 0) throw ApiException.bad("Maaş mənfi ola bilməz");
        e.name = r.name().trim();
        e.jobTitle = r.position() == null || r.position().isBlank() ? null : r.position().trim();
        e.workplace = r.workplace();
        e.gross = Money.r2(r.gross());
        e.active = r.active() == null || r.active();
        e.bankName = blank(r.bankName()); e.iban = blank(r.iban()); e.cardNumber = blank(r.cardNumber()); e.fin = blank(r.fin()); e.note = blank(r.note());
        e.cardExpiry = parseYm(r.cardExpiry());
        return employees.save(e);
    }

    @Transactional
    public Dto.EmployeeRes createEmployee(Dto.EmployeeReq r) { return toDto(apply(new E.Employee(), r)); }

    @Transactional
    public Dto.EmployeeRes updateEmployee(Long id, Dto.EmployeeReq r) {
        return toDto(apply(employees.findById(id).orElseThrow(() -> ApiException.notFound("İşçi tapılmadı")), r));
    }

    @Transactional
    public void deleteEmployee(Long id) {
        employees.delete(employees.findById(id).orElseThrow(() -> ApiException.notFound("İşçi tapılmadı")));
    }

    // ---- cədvəllər ----
    E.PayrollRun run(Long id) { return runs.findById(id).orElseThrow(() -> ApiException.notFound("Maaş cədvəli tapılmadı")); }

    private static Dto.LineRes lineDto(E.PayrollLine l) {
        return new Dto.LineRes(l.id, l.employeeId, l.name, l.jobTitle, l.workplace, Money.s(l.gross), l.normDays, l.workedDays,
                Money.s(l.accrued), Money.s(l.income), Money.s(l.dsmfEmp), Money.s(l.medEmp), Money.s(l.unempEmp), Money.s(l.net),
                Money.s(l.dsmfEr), Money.s(l.medEr), Money.s(l.unempEr), Money.s(l.employerCost));
    }

    /** Sətirlərin cəmi (sətirlə eyni sahələr; id/ad boş) */
    static Dto.LineRes totals(List<E.PayrollLine> ls, int normDays) {
        BigDecimal[] t = new BigDecimal[11];
        java.util.Arrays.fill(t, BigDecimal.ZERO);
        for (E.PayrollLine l : ls) {
            BigDecimal[] v = {l.gross, l.accrued, l.income, l.dsmfEmp, l.medEmp, l.unempEmp, l.net, l.dsmfEr, l.medEr, l.unempEr, l.employerCost};
            for (int i = 0; i < t.length; i++) t[i] = t[i].add(v[i]);
        }
        return new Dto.LineRes(null, null, "Cəmi", "", "", Money.s(t[0]), normDays, 0, Money.s(t[1]), Money.s(t[2]), Money.s(t[3]),
                Money.s(t[4]), Money.s(t[5]), Money.s(t[6]), Money.s(t[7]), Money.s(t[8]), Money.s(t[9]), Money.s(t[10]));
    }

    public List<Dto.RunSummary> listRuns() {
        List<Dto.RunSummary> out = new ArrayList<>();
        for (E.PayrollRun r : runs.findAll().stream().sorted(Comparator.comparing((E.PayrollRun x) -> x.period).reversed()).toList()) {
            List<E.PayrollLine> ls = lines.findByRunIdOrderById(r.id);
            Dto.LineRes t = totals(ls, r.normDays);
            out.add(new Dto.RunSummary(r.id, r.period, r.status, t.accrued(), t.net(), t.employerCost()));
        }
        return out;
    }

    public Dto.RunRes get(Long id) {
        E.PayrollRun r = run(id);
        List<E.PayrollLine> ls = lines.findByRunIdOrderById(id);
        return new Dto.RunRes(r.id, r.period, r.status, r.normDays, ls.stream().map(PayrollService::lineDto).toList(), totals(ls, r.normDays));
    }

    /** Ay üçün cədvəl yaradır: aktiv işçilərdən sətirlər, bütün norma günü işlənmiş kimi */
    @Transactional
    public Dto.RunRes create(String month) {
        if (month == null || !month.matches("\\d{4}-(0[1-9]|1[0-2])")) throw ApiException.bad("Ay YYYY-MM formatında olmalıdır");
        if (runs.existsByPeriod(month)) throw ApiException.conflict("cedvel_movcuddur", "Bu ay üçün maaş cədvəli artıq var");
        int norm = settings.normDays(month);
        if (norm <= 0) throw ApiException.conflict("norma_yoxdur", "Bu ayın iş günü sayı iş günü cədvəlində yoxdur: " + Xlsx.my(month));
        E.PayrollRate rate = settings.rateFor(month);
        E.PayrollRun r = new E.PayrollRun();
        r.period = month;
        r.status = "DRAFT";
        r.normDays = norm;
        r = runs.save(r);
        for (E.Employee e : employees.findAll().stream().filter(x -> x.active).sorted(Comparator.comparing(x -> x.id)).toList()) {
            E.PayrollLine l = new E.PayrollLine();
            l.runId = r.id;
            l.employeeId = e.id;
            l.name = e.name;
            l.jobTitle = e.jobTitle;
            l.workplace = e.workplace;
            l.gross = e.gross;
            l.normDays = norm;
            l.workedDays = norm;
            fill(l, calc(rate, e.gross, e.workplace, norm, norm));
            lines.save(l);
        }
        return get(r.id);
    }

    @Transactional
    public Dto.RunRes updateLine(Long runId, Long lineId, Integer workedDays) {
        E.PayrollRun r = run(runId);
        if (!"DRAFT".equals(r.status)) throw ApiException.conflict("cedvel_tesdiqlenib", "Təsdiqlənmiş cədvəl dəyişmir, əvvəl yenidən açın");
        E.PayrollLine l = lines.findById(lineId).filter(x -> x.runId.equals(runId)).orElseThrow(() -> ApiException.notFound("Sətir tapılmadı"));
        if (workedDays == null || workedDays < 0 || workedDays > r.normDays) throw ApiException.bad("Faktiki iş günü 0 ilə " + r.normDays + " arasında olmalıdır");
        l.workedDays = workedDays;
        fill(l, calc(settings.rateFor(r.period), l.gross, l.workplace, l.normDays, workedDays));
        lines.save(l);
        return get(runId);
    }

    @Transactional
    public Dto.RunRes setStatus(Long id, String status) {
        E.PayrollRun r = run(id);
        r.status = status;
        runs.save(r);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        E.PayrollRun r = run(id);
        if (!"DRAFT".equals(r.status)) throw ApiException.conflict("cedvel_tesdiqlenib", "Təsdiqlənmiş cədvəl silinə bilməz, əvvəl yenidən açın");
        List<Long> fids = runFiles.findByRunIdOrderById(id).stream().map(f -> f.fileId).toList();
        paylinks.deleteByRunId(id);
        runFiles.deleteByRunId(id);
        lines.deleteByRunId(id);
        runs.delete(r);
        runs.flush();
        for (Long f : fids) if (!fileSvc.isReferenced(f)) fileSvc.delete(f);
    }

    // ---- bank ödəniş kodları və ödənişlər ----
    private static Dto.PaymentCodeRes codeDto(E.PaymentCode c) {
        return new Dto.PaymentCodeRes(c.id, c.sortOrder, c.codeKey, c.title, c.budgetCode, c.active);
    }

    public List<Dto.PaymentCodeRes> listCodes() { return codes.findAllByOrderBySortOrderAscIdAsc().stream().map(PayrollService::codeDto).toList(); }

    @Transactional
    public Dto.PaymentCodeRes updateCode(Long id, Dto.PaymentCodeReq r) {
        E.PaymentCode c = codes.findById(id).orElseThrow(() -> ApiException.notFound("Ödəniş kodu tapılmadı"));
        if (r.title() == null || r.title().isBlank()) throw ApiException.bad("Təyinat boş ola bilməz");
        c.title = r.title().trim();
        c.budgetCode = r.budgetCode() == null || r.budgetCode().isBlank() ? null : r.budgetCode().trim();
        if (r.active() != null) c.active = r.active();
        if (r.sortOrder() != null) c.sortOrder = r.sortOrder();
        return codeDto(codes.save(c));
    }

    private static final String[] MONTHS = {"Yanvar", "Fevral", "Mart", "Aprel", "May", "İyun", "İyul", "Avqust", "Sentyabr", "Oktyabr", "Noyabr", "Dekabr"};
    private static final java.util.Map<String, String> PURPOSE = java.util.Map.of(
            "INCOME_TAX", "gəlir vergisi", "DSMF_EMP", "DSMF üzrə işçidən tutulan məbləğ", "DSMF_ER", "DSMF üzrə işəgötürənin vəsaiti",
            "MED_EMP", "icbari tibbi sığorta üzrə işçidən tutulan məbləğ", "MED_ER", "icbari tibbi sığorta üzrə işəgötürənin vəsaiti",
            "UNEMP_EMP", "işsizlikdən sığorta üzrə işçidən tutulan məbləğ", "UNEMP_ER", "işsizlikdən sığorta üzrə işəgötürənin vəsaiti",
            "NET", "əmək haqqı");

    static final java.util.Set<String> CODE_KEYS = java.util.Set.of("INCOME_TAX", "DSMF_EMP", "DSMF_ER", "MED_EMP", "MED_ER", "UNEMP_EMP", "UNEMP_ER", "NET");

    static String payStatus(BigDecimal amount, BigDecimal paid) {
        if (paid.compareTo(amount) >= 0 && paid.signum() > 0) return "PAID";
        return paid.signum() > 0 ? "PARTIAL" : "UNPAID";
    }

    private List<Dto.PayLink> payLinks(List<E.PayrollPayment> ps) {
        List<Dto.PayLink> out = new ArrayList<>();
        for (E.PayrollPayment p : ps) {
            java.time.LocalDate d = p.paidDate;
            if (d == null && p.movementId != null) d = movements.findById(p.movementId).map(m -> m.entryDate).orElse(null);
            out.add(new Dto.PayLink(p.id, p.movementId, d, Money.s(p.amount), p.note, p.fileId));
        }
        return out;
    }

    private static Long firstFile(List<E.PayrollPayment> ps) {
        return ps.stream().map(p -> p.fileId).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    /** Sətirin (vergi kodu və ya işçinin NET-i) məbləği */
    private BigDecimal lineAmount(Dto.RunRes r, String codeKey, Long employeeId) {
        if (employeeId != null) {
            if (!"NET".equals(codeKey)) throw ApiException.bad("İşçi üzrə yalnız NET ödənişi bağlana bilər");
            return r.lines().stream().filter(l -> employeeId.equals(l.employeeId())).findFirst()
                    .map(l -> new BigDecimal(l.net())).orElseThrow(() -> ApiException.notFound("Cədvəldə bu işçi yoxdur"));
        }
        Dto.LineRes t = r.totals();
        return new BigDecimal(switch (codeKey) {
            case "INCOME_TAX" -> t.income(); case "DSMF_EMP" -> t.dsmfEmp(); case "DSMF_ER" -> t.dsmfEr();
            case "MED_EMP" -> t.medEmp(); case "MED_ER" -> t.medEr(); case "UNEMP_EMP" -> t.unempEmp(); case "UNEMP_ER" -> t.unempEr();
            case "NET" -> t.net();
            default -> throw ApiException.bad("Ödəniş kodu yanlışdır");
        });
    }

    /** Aktiv kodlar üzrə məbləği 0-dan böyük olan ödənişlər (ABB Biznes-ə sətir-sətir köçürmək üçün) + bank hərəkətlərinə bağlantı statusu */
    public Dto.PaymentsRes payments(Long id) {
        Dto.RunRes r = get(id);
        Dto.LineRes t = r.totals();
        List<E.PayrollPayment> all = paylinks.findByRunIdOrderById(id);
        java.util.Map<String, String> amounts = java.util.Map.of("INCOME_TAX", t.income(), "DSMF_EMP", t.dsmfEmp(), "DSMF_ER", t.dsmfEr(),
                "MED_EMP", t.medEmp(), "MED_ER", t.medEr(), "UNEMP_EMP", t.unempEmp(), "UNEMP_ER", t.unempEr(), "NET", t.net());
        String[] ym = r.month().split("-");
        String prefix = MONTHS[Integer.parseInt(ym[1]) - 1] + " " + ym[0] + " ayı üzrə ";
        List<Dto.PaymentRes> out = new ArrayList<>();
        int n = 0;
        for (E.PaymentCode c : codes.findAllByOrderBySortOrderAscIdAsc()) {
            String amt = amounts.get(c.codeKey);
            if (!c.active || amt == null || new BigDecimal(amt).signum() <= 0) continue;
            List<E.PayrollPayment> ps = all.stream().filter(p -> p.codeKey.equals(c.codeKey)).toList();
            BigDecimal paid = Money.sum(ps.stream().map(p -> p.amount).toList());
            out.add(new Dto.PaymentRes(++n, c.codeKey, c.title, c.budgetCode, amt, prefix + PURPOSE.getOrDefault(c.codeKey, c.title.toLowerCase()),
                    payStatus(new BigDecimal(amt), paid), Money.s(paid), firstFile(ps), payLinks(ps)));
        }
        List<Dto.EmployeePaymentRes> emps = new ArrayList<>();
        // Maaş bank layihəsi ilə bir ödənişlə gedəndə ümumi NET sətrinə bağlanır (employeeId boş):
        // onda işçilər həmin ödənişin örtdüyü nisbətdə ödənilmiş sayılır
        List<E.PayrollPayment> batch = all.stream().filter(p -> "NET".equals(p.codeKey) && p.employeeId == null).toList();
        BigDecimal batchPaid = Money.sum(batch.stream().map(p -> p.amount).toList());
        BigDecimal netTotal = new BigDecimal(t.net());
        for (E.PayrollLine l : lines.findByRunIdOrderById(id)) {
            if (l.net.signum() <= 0) continue;
            E.Employee e = l.employeeId == null ? null : employees.findById(l.employeeId).orElse(null);
            List<E.PayrollPayment> ps = all.stream().filter(p -> "NET".equals(p.codeKey) && l.employeeId != null && l.employeeId.equals(p.employeeId)).toList();
            BigDecimal paid = Money.sum(ps.stream().map(p -> p.amount).toList());
            if (ps.isEmpty() && batchPaid.signum() > 0 && netTotal.signum() > 0) {
                ps = batch;
                paid = batchPaid.compareTo(netTotal) >= 0 ? l.net : Money.r2(l.net.multiply(batchPaid).divide(netTotal, 6, java.math.RoundingMode.HALF_UP));
            }
            emps.add(new Dto.EmployeePaymentRes(l.employeeId, l.name, e == null ? null : e.bankName, e == null ? null : e.iban, e == null ? null : e.cardNumber, e == null ? null : ym(e.cardExpiry),
                    Money.s(l.net), prefix + "əmək haqqı", payStatus(l.net, paid), Money.s(paid), firstFile(ps), payLinks(ps)));
        }
        return new Dto.PaymentsRes(out, emps);
    }

    private BigDecimal movementLinked(Long movementId) {
        return Money.sum(paylinks.findByMovementId(movementId).stream().map(p -> p.amount).toList());
    }

    private BigDecimal lineRemaining(Dto.RunRes r, List<E.PayrollPayment> all, String codeKey, Long employeeId) {
        BigDecimal amt = lineAmount(r, codeKey, employeeId);
        BigDecimal paid = Money.sum(all.stream().filter(p -> p.codeKey.equals(codeKey) && (employeeId == null || employeeId.equals(p.employeeId))).map(p -> p.amount).toList());
        return amt.subtract(paid).max(BigDecimal.ZERO);
    }

    /** Sətri bank hərəkətinə bağlayır; amount verilməyəndə = min(hərəkətin boş qalığı, sətrin qalığı) */
    @Transactional
    public Dto.PaymentsRes link(Long runId, Dto.PayLinkReq q) {
        Dto.RunRes r = get(runId);
        if (q.codeKey() == null || !CODE_KEYS.contains(q.codeKey())) throw ApiException.bad("Ödəniş kodu yanlışdır");
        if (q.movementId() == null) throw ApiException.bad("Bank əməliyyatı seçilməlidir");
        E.Movement m = movements.findById(q.movementId()).orElseThrow(() -> ApiException.notFound("Hərəkət tapılmadı"));
        if (!"OUT".equals(m.direction)) throw ApiException.bad("Yalnız çıxış (OUT) hərəkəti bağlana bilər");
        BigDecimal movRem = m.amount.subtract(movementLinked(m.id));
        BigDecimal lineRem = lineRemaining(r, paylinks.findByRunIdOrderById(runId), q.codeKey(), q.employeeId());
        BigDecimal amount = q.amount() == null ? movRem.min(lineRem) : Money.r2(q.amount());
        if (amount.signum() <= 0) throw ApiException.conflict("setir_odenilib", "Bu sətir artıq tam ödənilib və ya hərəkətin boş qalığı yoxdur");
        if (amount.compareTo(movRem) > 0) throw ApiException.conflict("artiq_bolusdurme", "Bağlanan məbləğ hərəkətin boş qalığından (" + Money.s(movRem) + " ₼) çoxdur");
        if (amount.compareTo(lineRem) > 0) throw ApiException.conflict("artiq_odenis", "Bağlanan məbləğ sətrin qalığından (" + Money.s(lineRem) + " ₼) çoxdur");
        E.PayrollPayment p = new E.PayrollPayment();
        p.runId = runId; p.codeKey = q.codeKey(); p.employeeId = q.employeeId(); p.amount = amount;
        p.movementId = m.id; p.paidDate = m.entryDate; p.fileId = q.fileId(); p.note = blank(q.note());
        paylinks.save(p);
        return payments(runId);
    }

    @Transactional
    public void unlink(Long linkId) {
        paylinks.delete(paylinks.findById(linkId).orElseThrow(() -> ApiException.notFound("Ödəniş bağlantısı tapılmadı")));
    }

    private static final String[] KEYWORDS = {"maaş", "əmək haqq", "salary", "vergi", "dsmf", "sığorta", "tax", "tibbi", "işsizlik"};

    /** Bağlanmamış (boş qalığı olan) bank OUT hərəkətləri (təyinat SALARY/TAX): dəqiq məbləğ, dövr tarixi, açar sözlər üzrə sıralanır */
    public List<Dto.MovementSuggestion> suggestions(Long runId, String codeKey, Long employeeId) {
        Dto.RunRes r = get(runId);
        if (codeKey == null || !CODE_KEYS.contains(codeKey)) throw ApiException.bad("Ödəniş kodu yanlışdır");
        BigDecimal lineRem = lineRemaining(r, paylinks.findByRunIdOrderById(runId), codeKey, employeeId);
        java.time.YearMonth ym = java.time.YearMonth.parse(r.month());
        java.time.LocalDate from = ym.atDay(1), to = ym.atEndOfMonth().plusDays(45);
        String wantPurpose = "NET".equals(codeKey) ? "SALARY" : "TAX";
        java.util.Map<Long, E.Account> accs = new java.util.HashMap<>();
        accounts.findAll().forEach(a -> accs.put(a.id, a));
        List<Dto.MovementSuggestion> out = new ArrayList<>();
        for (E.Movement m : movements.findAll()) {
            E.Account a = accs.get(m.accountId);
            if (!"OUT".equals(m.direction) || a == null || !"BANK".equals(a.type)) continue;
            if (!"SALARY".equals(m.purpose) && !"TAX".equals(m.purpose)) continue;
            BigDecimal rem = m.amount.subtract(movementLinked(m.id));
            if (rem.signum() <= 0) continue;
            int score = 0;
            List<String> why = new ArrayList<>();
            if (lineRem.signum() > 0 && rem.compareTo(lineRem) == 0) { score += 100; why.add("məbləğ eynidir"); }
            if (!m.entryDate.isBefore(from) && !m.entryDate.isAfter(to)) { score += 30; why.add("tarix dövrə uyğundur"); }
            if (wantPurpose.equals(m.purpose)) { score += 20; why.add("təyinat uyğundur"); }
            String note = m.note == null ? "" : m.note.toLowerCase();
            for (String k : KEYWORDS) if (note.contains(k)) { score += 10; why.add("qeyddə açar söz"); break; }
            out.add(new Dto.MovementSuggestion(m.id, m.entryDate, a.name, Money.s(m.amount), Money.s(rem), m.purpose, m.note, score, why));
        }
        out.sort(Comparator.comparingInt(Dto.MovementSuggestion::score).reversed().thenComparing(Dto.MovementSuggestion::date, Comparator.reverseOrder()).thenComparing(Dto.MovementSuggestion::movementId));
        return out;
    }

    /** İdarə paneli: yekunlaşmış (FINAL) cədvəllərdən (dövr >= 2026-08) ödənilməmiş/qismən ödənilmiş vergi və maaş sətirləri */
    public List<Dto.UnpaidRun> unpaid() {
        List<Dto.UnpaidRun> out = new ArrayList<>();
        for (E.PayrollRun r : runs.findAll().stream().filter(x -> "FINAL".equals(x.status) && x.period.compareTo("2026-08") >= 0)
                .sorted(Comparator.comparing((E.PayrollRun x) -> x.period)).toList()) {
            List<Dto.UnpaidLine> ls = new ArrayList<>();
            for (Dto.PaymentRes p : payments(r.id).taxes())
                if (!"PAID".equals(p.status())) ls.add(new Dto.UnpaidLine(p.key(), p.title(), p.amount(), p.paidAmount(), p.status()));
            if (!ls.isEmpty()) out.add(new Dto.UnpaidRun(r.id, r.period, ls));
        }
        return out;
    }

    // ---- cədvəlin sənədləri ----
    private Dto.RunFileRes fileDto(E.PayrollRunFile f) {
        E.StoredFile sf = fileSvc.meta(f.fileId);
        return new Dto.RunFileRes(f.id, f.fileId, f.title, sf.name, sf.sizeBytes, f.createdAt.toString());
    }

    public List<Dto.RunFileRes> listFiles(Long runId) {
        run(runId);
        return runFiles.findByRunIdOrderById(runId).stream().map(this::fileDto).toList();
    }

    @Transactional
    public Dto.RunFileRes addFile(Long runId, org.springframework.web.multipart.MultipartFile file, String title) {
        run(runId);
        Dto.FileRes fr = fileSvc.store(file);
        E.PayrollRunFile f = new E.PayrollRunFile();
        f.runId = runId; f.fileId = fr.id(); f.title = blank(title) == null ? fr.name() : title.trim(); f.createdAt = java.time.LocalDateTime.now();
        return fileDto(runFiles.save(f));
    }

    @Transactional
    public void deleteFile(Long runId, Long runFileId) {
        E.PayrollRunFile f = runFiles.findById(runFileId).filter(x -> x.runId.equals(runId)).orElseThrow(() -> ApiException.notFound("Sənəd tapılmadı"));
        runFiles.delete(f);
        runFiles.flush();
        if (!fileSvc.isReferenced(f.fileId)) fileSvc.delete(f.fileId);
    }

    public byte[] xlsx(Long id) {
        Dto.RunRes r = get(id);
        Xlsx x = new Xlsx("Maaş " + Xlsx.my(r.month()));
        x.title("Maaş cədvəli — " + Xlsx.my(r.month()) + " (" + r.status() + ", norma " + r.normDays() + " gün)");
        x.header("İşçi", "Vəzifə", "İş yeri", "Müqavilə maaşı", "Norma gün", "Faktiki gün", "Hesablanmış", "Gəlir vergisi", "DSMF işçi",
                "İTS işçi", "İşsizlik işçi", "NET", "DSMF şirkət", "İTS şirkət", "İşsizlik şirkət", "İşəgötürən xərci");
        for (Dto.LineRes l : r.lines()) x.row(l.name(), l.position(), "MAIN".equals(l.workplace()) ? "Əsas" : "Əlavə", l.gross(), l.normDays(),
                l.workedDays(), l.accrued(), l.income(), l.dsmfEmp(), l.medEmp(), l.unempEmp(), l.net(), l.dsmfEr(), l.medEr(), l.unempEr(), l.employerCost());
        Dto.LineRes t = r.totals();
        x.bold("Cəmi", "", "", t.gross(), "", "", t.accrued(), t.income(), t.dsmfEmp(), t.medEmp(), t.unempEmp(), t.net(), t.dsmfEr(), t.medEr(), t.unempEr(), t.employerCost());
        x.blank();
        x.title("Bank ödənişləri");
        x.row("Gəlir vergisi", t.income());
        x.row("DSMF işçi", t.dsmfEmp());
        x.row("DSMF şirkət", t.dsmfEr());
        x.row("İTS işçi", t.medEmp());
        x.row("İTS şirkət", t.medEr());
        x.row("İşsizlik işçi", t.unempEmp());
        x.row("İşsizlik şirkət", t.unempEr());
        x.row("NET maaşlar", t.net());
        x.bold("Cəmi", t.employerCost());

        x.sheet("Bank ödənişləri");
        x.header("№", "Təyinat", "Büdcə kodu", "Məbləğ", "Ödəniş təyinatı");
        BigDecimal sum = BigDecimal.ZERO;
        Dto.PaymentsRes pays = payments(id);
        for (Dto.PaymentRes p : pays.taxes()) {
            x.row(p.order(), p.title(), p.budgetCode(), p.amount(), p.purpose());
            sum = sum.add(new BigDecimal(p.amount()));
        }
        x.bold("", "Cəmi", "", Money.s(sum), "");
        x.blank();
        x.title("İşçilər üzrə maaş ödənişləri");
        x.header("№", "İşçi", "Bank hesabı", "IBAN", "Kart", "Məbləğ", "Ödəniş təyinatı");
        int n = 0;
        for (Dto.EmployeePaymentRes e : pays.employees()) x.row(++n, e.name(), e.bankName(), e.iban(), e.cardNumber(), e.amount(), e.purpose());
        return x.bytes();
    }
}
