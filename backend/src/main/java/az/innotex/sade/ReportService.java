package az.innotex.sade;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/** İdarə paneli və rüblük/illik hesabat (SPEC §1, §7). Proqram e-taxes-a göndərmir, rəqəmləri hazırlayır. */
@Service
public class ReportService {
    private final R.Deals deals;
    private final PayrollService payrollSvc;
    private final R.Customers customers;
    private final R.Products products;
    private final R.Expenses expenses;
    private final R.Movements movements;
    private final R.Accounts accounts;
    private final R.Runs runs;
    private final R.Lines lines;
    private final SettingsService settings;
    private final LedgerService ledger;
    private final R.Employees employees;
    private final PaymentService payments;
    private final R.Events events;

    public ReportService(R.Deals deals, R.Customers customers, R.Products products, R.Expenses expenses, R.Movements movements, R.Accounts accounts,
                         R.Runs runs, R.Lines lines, SettingsService settings, LedgerService ledger, R.Employees employees, PaymentService payments, PayrollService payrollSvc, R.Events events) {
        this.events = events;
        this.payrollSvc = payrollSvc;
        this.payments = payments;
        this.employees = employees;
        this.deals = deals;
        this.customers = customers;
        this.products = products;
        this.expenses = expenses;
        this.movements = movements;
        this.accounts = accounts;
        this.runs = runs;
        this.lines = lines;
        this.settings = settings;
        this.ledger = ledger;
    }

    static LocalDate qStart(int year, int q) { return LocalDate.of(year, (q - 1) * 3 + 1, 1); }
    static LocalDate qEnd(int year, int q) { return YearMonth.of(year, q * 3).atEndOfMonth(); }

    private static boolean in(LocalDate d, LocalDate from, LocalDate to) { return d != null && !d.isBefore(from) && !d.isAfter(to); }

    private BigDecimal tax(BigDecimal profit, BigDecimal rate) {
        return profit.signum() <= 0 ? Money.ZERO : Money.r2(profit.multiply(rate).divide(Money.HUNDRED, 6, RoundingMode.HALF_UP));
    }

    /** Dövr üzrə əsas rəqəmlər */
    private record Agg(BigDecimal income, List<Dto.IncomeItem> items, Map<String, BigDecimal> byProduct, BigDecimal deductible, Map<String, BigDecimal> byCategory,
                       BigDecimal bankFees, BigDecimal payrollCost, List<Dto.PayrollMonth> months, Dto.PayrollMonth total) {
        BigDecimal expenses() { return deductible.add(bankFees).add(payrollCost); }
        BigDecimal profit() { return income.subtract(expenses()); }
    }

    private static Dto.PayrollMonth month(String m, BigDecimal[] t) {
        return new Dto.PayrollMonth(m, Money.s(t[0]), Money.s(t[1]), Money.s(t[2]), Money.s(t[3]), Money.s(t[4]), Money.s(t[5]), Money.s(t[6]), Money.s(t[7]), Money.s(t[8]));
    }

    /** Fiziki şəxs satışının gəlir tarixi: DONE mərhələsinə keçid tarixçəsinin tarixi; yoxdursa ən son ödəniş bağlantısının hərəkət tarixi; yoxdursa satış tarixi */
    private LocalDate retailIncomeDate(E.Deal d) {
        LocalDate done = events.findByDealIdOrderByIdDesc(d.id).stream().filter(e -> "DONE".equals(e.toStage)).map(e -> e.eventDate != null ? e.eventDate : e.createdAt.toLocalDate()).findFirst().orElse(null);
        if (done != null) return done;
        LocalDate last = payments.forDealRaw(d.id).stream().map(a -> movements.findById(a.movementId).map(m -> m.entryDate).orElse(null)).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        return last != null ? last : d.saleDate;
    }

    private Agg aggregate(LocalDate from, LocalDate to) {
        Map<Long, E.Customer> cs = new HashMap<>();
        customers.findAll().forEach(c -> cs.put(c.id, c));
        // gəlir: yekun qaimə tarixi dövrdə olan satışlar (avans gəlir deyil)
        List<Dto.IncomeItem> items = new ArrayList<>();
        BigDecimal income = BigDecimal.ZERO;
        Map<Long, String> pn = new HashMap<>();
        products.findAll().forEach(p -> pn.put(p.id, p.name));
        Map<String, BigDecimal> byProd = new LinkedHashMap<>();
        record Inc(LocalDate date, long id, Dto.IncomeItem item) {}
        List<Inc> incs = new ArrayList<>();
        for (E.Deal d : deals.findAll()) {
            if (d.cancelled) continue;
            E.Customer c = cs.get(d.customerId);
            String pname = d.productId != null && pn.get(d.productId) != null ? pn.get(d.productId) : (d.product == null || d.product.isBlank() ? "—" : d.product.trim());
            if ("RETAIL".equals(d.paymentTerms)) {
                if (!"DONE".equals(d.stage)) continue;
                LocalDate dt = retailIncomeDate(d);
                if (!in(dt, from, to)) continue;
                incs.add(new Inc(dt, d.id, new Dto.IncomeItem(dt, null, c == null ? "" : c.name, Money.s(d.price), "RETAIL")));
                income = income.add(d.price);
                byProd.merge(pname, d.price, BigDecimal::add);
            } else if (("INVOICE".equals(d.stage) || "DONE".equals(d.stage)) && d.invoiceAmount != null && in(d.invoiceDate, from, to)) {
                incs.add(new Inc(d.invoiceDate, d.id, new Dto.IncomeItem(d.invoiceDate, d.invoiceNo, c == null ? "" : c.name, Money.s(d.invoiceAmount), "INVOICE")));
                income = income.add(d.invoiceAmount);
                byProd.merge(pname, d.invoiceAmount, BigDecimal::add);
            }
        }
        incs.sort(Comparator.comparing(Inc::date).thenComparing(Inc::id));
        incs.forEach(i -> items.add(i.item()));
        // xərclər
        BigDecimal deductible = BigDecimal.ZERO;
        Map<String, BigDecimal> byCat = new TreeMap<>();
        for (E.Expense e : expenses.findAll()) {
            if (!e.deductible || !in(e.docDate, from, to)) continue;
            deductible = deductible.add(e.amountAzn);
            byCat.merge(e.category, e.amountAzn, BigDecimal::add);
        }
        BigDecimal fees = BigDecimal.ZERO;
        for (E.Movement m : movements.findAll()) {
            if (!"BANK_FEE".equals(m.purpose) || !in(m.entryDate, from, to)) continue;
            fees = "OUT".equals(m.direction) ? fees.add(m.amount) : fees.subtract(m.amount);
        }
        // maaş: yalnız FINAL cədvəllər
        String a = YearMonth.from(from).toString(), b = YearMonth.from(to).toString();
        List<Dto.PayrollMonth> months = new ArrayList<>();
        BigDecimal[] tot = new BigDecimal[9];
        Arrays.fill(tot, BigDecimal.ZERO);
        BigDecimal cost = BigDecimal.ZERO;
        for (E.PayrollRun r : runs.findAll().stream().sorted(Comparator.comparing(x -> x.period)).toList()) {
            if (!"FINAL".equals(r.status) || r.period.compareTo(a) < 0 || r.period.compareTo(b) > 0) continue;
            BigDecimal[] t = new BigDecimal[9];
            Arrays.fill(t, BigDecimal.ZERO);
            for (E.PayrollLine l : lines.findByRunIdOrderById(r.id)) {
                BigDecimal[] v = {l.accrued, l.income, l.dsmfEmp, l.dsmfEr, l.medEmp, l.medEr, l.unempEmp, l.unempEr, l.net};
                for (int i = 0; i < 9; i++) t[i] = t[i].add(v[i]);
                cost = cost.add(l.employerCost);
            }
            for (int i = 0; i < 9; i++) tot[i] = tot[i].add(t[i]);
            months.add(month(r.period, t));
        }
        Map<String, BigDecimal> cats = new LinkedHashMap<>();
        byCat.entrySet().stream().sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).forEach(e -> cats.put(e.getKey(), e.getValue()));
        Map<String, BigDecimal> prods = new LinkedHashMap<>();
        byProd.entrySet().stream().sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).forEach(e -> prods.put(e.getKey(), e.getValue()));
        return new Agg(Money.r2(income), items, prods, Money.r2(deductible), cats, Money.r2(fees), Money.r2(cost), months, month(null, tot));
    }

    public Dto.Report quarter(int year, int q) {
        if (q < 1 || q > 4) throw ApiException.bad("Rüb 1 ilə 4 arasında olmalıdır");
        return build(qStart(year, q), qEnd(year, q), null);
    }

    public Dto.Report year(int year) {
        List<Dto.QuarterSummary> qs = new ArrayList<>();
        BigDecimal rate = settings.entity().profitTaxRate;
        for (int q = 1; q <= 4; q++) {
            Agg a = aggregate(qStart(year, q), qEnd(year, q));
            qs.add(new Dto.QuarterSummary(q, Money.s(a.income()), Money.s(a.expenses()), Money.s(a.profit()), Money.s(tax(a.profit(), rate))));
        }
        return build(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), qs);
    }

    private Dto.Report build(LocalDate from, LocalDate to, List<Dto.QuarterSummary> quarters) {
        BigDecimal rate = settings.entity().profitTaxRate;
        Agg a = aggregate(from, to);
        List<Dto.CategoryAmount> cats = a.byCategory().entrySet().stream().map(e -> new Dto.CategoryAmount(e.getKey(), Money.s(e.getValue()))).toList();
        return new Dto.Report(new Dto.Period(from, to), Money.s(a.income()), a.items(), Money.s(a.expenses()), cats,
                a.byProduct().entrySet().stream().map(e -> new Dto.ProductAmount(e.getKey(), Money.s(e.getValue()))).toList(), Money.s(a.bankFees()),
                Money.s(a.payrollCost()), Money.s(a.profit()), Money.rate(rate), Money.s(tax(a.profit(), rate)),
                new Dto.PayrollBlock(a.months(), a.total()), quarters, "ACCRUAL", warnings(from, to));
    }

    /** Hesabata daxil olmayan/şübhəli məlumatlar barədə xəbərdarlıqlar (hesablama metodu) */
    private List<String> warnings(LocalDate from, LocalDate to) {
        List<String> w = new ArrayList<>();
        boolean anyActive = employees.findAll().stream().anyMatch(e -> e.active);
        if (anyActive) {
            Map<String, String> st = new HashMap<>();
            runs.findAll().forEach(r -> st.put(r.period, r.status));
            YearMonth last = YearMonth.now().isBefore(YearMonth.from(to)) ? YearMonth.now() : YearMonth.from(to);
            for (YearMonth m = YearMonth.from(from); !m.isAfter(last); m = m.plusMonths(1)) {
                if (!"FINAL".equals(st.get(m.toString())))
                    w.add(String.format("%02d.%d maaş cədvəli yekunlaşdırılmayıb — hesabata daxil deyil", m.getMonthValue(), m.getYear()));
            }
        }
        for (E.Deal d : deals.findAll()) {
            if (d.cancelled || !"RETAIL".equals(d.paymentTerms) || !"DONE".equals(d.stage) || !in(retailIncomeDate(d), from, to)) continue;
            PaymentService.Summary s = PaymentService.summary(d, payments.forDealRaw(d.id));
            if (s.remaining().signum() > 0) {
                String cn = customers.findById(d.customerId).map(c -> c.name).orElse("");
                w.add("Fiziki şəxs satışı tamamlanıb, lakin tam ödənilməyib (" + cn + ", qalıq " + Money.s(s.remaining()) + " ₼) — gəlirə daxildir");
            }
        }
        long chk = expenses.findAll().stream().filter(e -> e.deductible && in(e.docDate, from, to) && e.note != null && e.note.trim().toUpperCase(java.util.Locale.ROOT).startsWith("YOXLAYIN")).count();
        if (chk > 0) w.add(chk + " xərcin qeydi \"YOXLAYIN\" ilə başlayır — məbləğ/tarix təsdiqlənməyib");
        return w;
    }

    public Dto.Dashboard dashboard() {
        LocalDate today = LocalDate.now();
        int q = (today.getMonthValue() - 1) / 3 + 1;
        Dto.Report r = quarter(today.getYear(), q);
        List<E.Movement> all = movements.findAll();
        List<Dto.DashAccount> accs = accounts.findAll().stream().sorted(Comparator.comparing(x -> x.id))
                .map(x -> new Dto.DashAccount(x.id, x.name, Money.s(ledger.balance(x, all)))).toList();
        // direktora borc = şəxsi kartdan ödənmiş xərclər − direktora qaytarış
        Set<Long> cards = new HashSet<>();
        accounts.findAll().stream().filter(x -> "DIRECTOR_CARD".equals(x.type)).forEach(x -> cards.add(x.id));
        BigDecimal paid = BigDecimal.ZERO, repaid = BigDecimal.ZERO;
        for (E.Movement m : all) {
            if ("OUT".equals(m.direction) && cards.contains(m.accountId) && !"OWNER_REPAYMENT".equals(m.purpose) && !"TRANSFER".equals(m.purpose)) paid = paid.add(m.amount);
            if ("OUT".equals(m.direction) && "OWNER_REPAYMENT".equals(m.purpose)) repaid = repaid.add(m.amount);
        }
        Map<String, Integer> byStage = new LinkedHashMap<>();
        SalesService.STAGES.forEach(s -> byStage.put(s, 0));
        deals.findAll().stream().filter(d -> !d.cancelled).forEach(d -> byStage.merge(d.stage, 1, Integer::sum));
        return new Dto.Dashboard(new Dto.DashQuarter(today.getYear(), q, r.income(), r.expenses(), r.profit(), r.profitTax()), accs,
                Money.s(paid.subtract(repaid)), byStage, expiringCards(today), awaitingPayments(), r.method(), r.warnings(), payrollSvc.unpaid());
    }

    /** Ödəniş gözlənilən satışlar (qalıq > 0; qaimə/tamamlanıb və ya avans ödənilməyib), ən köhnə qaimə əvvəl */
    private List<Dto.AwaitingPayment> awaitingPayments() {
        Map<Long, String> names = new HashMap<>();
        customers.findAll().forEach(c -> names.put(c.id, c.name));
        Map<Long, List<E.PaymentAllocation>> al = payments.byDeal();
        List<Dto.AwaitingPayment> out = new ArrayList<>();
        deals.findAll().stream().filter(d -> !d.cancelled).sorted(Comparator.comparing((E.Deal d) -> d.invoiceDate == null ? (d.advanceInvoiceDate == null ? d.saleDate : d.advanceInvoiceDate) : d.invoiceDate).thenComparing(d -> d.id)).forEach(d -> {
            PaymentService.Summary s = PaymentService.summary(d, al.getOrDefault(d.id, List.of()));
            if (PaymentService.awaiting(d, s))
                out.add(new Dto.AwaitingPayment(d.id, names.get(d.customerId), d.contractNo, d.invoiceDate != null ? d.invoiceDate : d.advanceInvoiceDate, Money.s(s.price()), Money.s(s.paid()), Money.s(s.remaining())));
        });
        return out;
    }

    /** Bitmə tarixi 60 gün ərzində olan və ya keçmiş kartlar (aktiv işçilər); ən tələskən əvvəl. */
    private List<Dto.ExpiringCard> expiringCards(LocalDate today) {
        return employees.findAll().stream().filter(e -> e.active && e.cardExpiry != null)
                .map(e -> new Dto.ExpiringCard(e.id, e.name, YearMonth.from(e.cardExpiry).toString(), java.time.temporal.ChronoUnit.DAYS.between(today, e.cardExpiry)))
                .filter(c -> c.daysLeft() <= 60).sorted(Comparator.comparingLong(Dto.ExpiringCard::daysLeft)).toList();
    }

    // ---- Excel ----
    public byte[] xlsx(Dto.Report r, String title) {
        Xlsx x = new Xlsx("Hesabat");
        x.title(title + " (" + Xlsx.dmy(r.period().from()) + " — " + Xlsx.dmy(r.period().to()) + ")");
        x.row("Vergi uçotu metodu: Hesablama metodu");
        for (String w : r.warnings()) x.row("Xəbərdarlıq: " + w);
        x.blank();
        x.title("Gəlir (yekun qaimə tarixi ilə; fiziki şəxs — tamamlanma tarixi ilə)");
        x.header("Tarix", "Qaimə №", "Müştəri", "Məbləğ");
        for (Dto.IncomeItem i : r.incomeItems()) x.row(i.date(), "RETAIL".equals(i.kind()) ? "—" : i.invoiceNo(), "RETAIL".equals(i.kind()) ? i.customer() + " (fiziki şəxs)" : i.customer(), i.amount());
        x.bold("Gəlir cəmi", "", "", r.income());
        x.blank();
        x.title("Gəlir məhsul üzrə");
        x.header("Məhsul", "Məbləğ");
        for (Dto.ProductAmount p : r.incomeByProduct()) x.row(p.product(), p.amount());
        x.blank();
        x.title("Xərclər (sənəd tarixi ilə)");
        x.header("Kateqoriya", "Məbləğ");
        for (Dto.CategoryAmount c : r.expenseByCategory()) x.row(c.category(), c.amount());
        x.row("Bank komissiyaları", r.bankFees());
        x.row("Əmək haqqı (işəgötürən xərci)", r.payrollCost());
        x.bold("Xərclər cəmi", r.expenses());
        x.blank();
        x.bold("Mənfəət", r.profit());
        x.row("Mənfəət vergisi dərəcəsi (%)", r.profitTaxRate());
        x.bold("Mənfəət vergisi", r.profitTax());
        x.blank();
        x.title("Əmək haqqı (FINAL cədvəllər)");
        x.header("Ay", "Hesablanmış", "Gəlir vergisi", "DSMF işçi", "DSMF şirkət", "İTS işçi", "İTS şirkət", "İşsizlik işçi", "İşsizlik şirkət", "NET");
        for (Dto.PayrollMonth m : r.payroll().months()) x.row(Xlsx.my(m.month()), m.gross(), m.income(), m.dsmfEmp(), m.dsmfEr(), m.medEmp(), m.medEr(), m.unempEmp(), m.unempEr(), m.net());
        Dto.PayrollMonth t = r.payroll().total();
        x.bold("Cəmi", t.gross(), t.income(), t.dsmfEmp(), t.dsmfEr(), t.medEmp(), t.medEr(), t.unempEmp(), t.unempEr(), t.net());
        if (r.quarters() != null) {
            x.blank();
            x.title("Rüblər");
            x.header("Rüb", "Gəlir", "Xərclər", "Mənfəət", "Mənfəət vergisi");
            for (Dto.QuarterSummary q : r.quarters()) x.row("Q" + q.q(), q.income(), q.expenses(), q.profit(), q.profitTax());
        }
        return x.bytes();
    }
}
