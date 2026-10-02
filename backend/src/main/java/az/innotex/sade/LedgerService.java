package az.innotex.sade;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Hesablar, hesab hərəkətləri və xərclər */
@Service
public class LedgerService {
    static final Set<String> ACCOUNT_TYPES = Set.of("BANK", "DIRECTOR_CARD", "CASH");
    static final Set<String> PURPOSES = Set.of("CUSTOMER_PAYMENT", "FOUNDER_LOAN", "CHARTER_CAPITAL", "EXPENSE", "SALARY", "TAX",
            "BANK_FEE", "TRANSFER", "OWNER_REPAYMENT", "OTHER");
    static final Set<String> CURRENCIES = Set.of("AZN", "USD", "EUR");

    private final R.Accounts accounts;
    private final R.Movements movements;
    private final R.Expenses expenses;
    private final R.ExpenseItems items;
    private final PaymentService payments;
    private final R.Statements statements;

    public LedgerService(R.Statements statements, R.Accounts accounts, R.Movements movements, R.Expenses expenses, R.ExpenseItems items, PaymentService payments) {
        this.statements = statements;
        this.items = items;
        this.accounts = accounts;
        this.movements = movements;
        this.expenses = expenses;
        this.payments = payments;
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    // ---- hesablar ----
    E.Account account(Long id) {
        return accounts.findById(id).orElseThrow(() -> ApiException.notFound("Hesab tapılmadı"));
    }

    /** Hesab qalığı = açılış qalığı + giriş − çıxış */
    BigDecimal balance(E.Account a, List<E.Movement> all) {
        BigDecimal b = Money.nz(a.openingBalance);
        for (E.Movement m : all) {
            if (!a.id.equals(m.accountId)) continue;
            b = "IN".equals(m.direction) ? b.add(m.amount) : b.subtract(m.amount);
        }
        return Money.r2(b);
    }

    private Dto.AccountRes toDto(E.Account a, List<E.Movement> all) {
        return new Dto.AccountRes(a.id, a.name, a.type, a.currency, Money.s(a.openingBalance), a.openingDate, Money.s(balance(a, all)),
                a.bankName, a.iban, a.bankCode, a.bankVoen, a.swift, a.correspondentAccount, a.cardNumber, a.note, a.isDefault);
    }

    public List<Dto.AccountRes> listAccounts() {
        List<E.Movement> all = movements.findAll();
        return accounts.findAll().stream().sorted(Comparator.comparing(a -> a.id)).map(a -> toDto(a, all)).toList();
    }

    private E.Account applyAccount(E.Account a, Dto.AccountReq r) {
        if (blank(r.name()) == null) throw ApiException.bad("Hesabın adı boş ola bilməz");
        if (r.type() == null || !ACCOUNT_TYPES.contains(r.type())) throw ApiException.bad("Hesab növü BANK, DIRECTOR_CARD və ya CASH olmalıdır");
        a.name = r.name().trim();
        a.type = r.type();
        a.currency = blank(r.currency()) == null ? "AZN" : r.currency().trim().toUpperCase();
        a.openingBalance = Money.r2(r.openingBalance());
        a.openingDate = r.openingDate();
        a.bankName = blank(r.bankName()); a.iban = blank(r.iban()); a.bankCode = blank(r.bankCode()); a.bankVoen = blank(r.bankVoen());
        a.swift = blank(r.swift()); a.correspondentAccount = blank(r.correspondentAccount()); a.cardNumber = blank(r.cardNumber()); a.note = blank(r.note());
        return accounts.save(a);
    }

    @Transactional
    public Dto.AccountRes createAccount(Dto.AccountReq r) {
        E.Account a = new E.Account();
        // əsas hesab hələ yoxdursa, ilk bank hesabı əsas olur
        a.isDefault = "BANK".equals(r.type()) && accounts.findAll().stream().noneMatch(x -> x.isDefault);
        return toDto(applyAccount(a, r), movements.findAll());
    }

    @Transactional
    public Dto.AccountRes updateAccount(Long id, Dto.AccountReq r) { return toDto(applyAccount(account(id), r), movements.findAll()); }

    /** Hesabı əsas edir (qalanlarından əsas nişanı götürülür) */
    @Transactional
    public Dto.AccountRes setDefaultAccount(Long id) {
        E.Account target = account(id);
        for (E.Account a : accounts.findAll()) {
            boolean d = a.id.equals(target.id);
            if (a.isDefault != d) { a.isDefault = d; accounts.save(a); }
        }
        return toDto(target, movements.findAll());
    }

    @Transactional
    public void deleteAccount(Long id) {
        E.Account a = account(id);
        if (movements.existsByAccountId(id) || expenses.existsByAccountId(id))
            throw ApiException.conflict("hesab_istifade_olunub", "Hesabda hərəkət və ya xərc var, silmək olmaz");
        accounts.delete(a);
    }

    // ---- hərəkətlər ----
    Dto.MovementRes toDto(E.Movement m, Map<Long, E.Account> accs) {
        E.Account a = accs.get(m.accountId);
        List<Dto.MovementAllocRes> al = "IN".equals(m.direction) ? payments.movementAllocations(m.id) : List.of();
        BigDecimal used = Money.r2(Money.sum(al.stream().map(x -> new BigDecimal(x.amount())).toList()));
        return new Dto.MovementRes(m.id, m.entryDate, m.accountId, a == null ? null : a.name, m.direction, Money.s(m.amount),
                m.purpose, m.note, m.fileId, m.dealId, m.expenseId, Money.s(used), al, m.statementId);
    }

    private Map<Long, E.Account> accountMap() { return accounts.findAll().stream().collect(Collectors.toMap(a -> a.id, Function.identity())); }

    public List<Dto.MovementRes> listMovements(Long accountId, LocalDate from, LocalDate to) {
        Map<Long, E.Account> accs = accountMap();
        return movements.findAll().stream()
                .filter(m -> accountId == null || accountId.equals(m.accountId))
                .filter(m -> from == null || !m.entryDate.isBefore(from))
                .filter(m -> to == null || !m.entryDate.isAfter(to))
                .sorted(Comparator.comparing((E.Movement m) -> m.entryDate).thenComparing(m -> m.id).reversed())
                .map(m -> toDto(m, accs)).toList();
    }

    private void checkMovement(Dto.MovementReq r) {
        if (r.date() == null) throw ApiException.bad("Tarix göstərilməlidir");
        if (r.accountId() == null) throw ApiException.bad("Hesab seçilməlidir");
        account(r.accountId());
        if (!"IN".equals(r.direction()) && !"OUT".equals(r.direction())) throw ApiException.bad("İstiqamət IN və ya OUT olmalıdır");
        if (r.amount() == null || r.amount().signum() <= 0) throw ApiException.bad("Məbləğ sıfırdan böyük olmalıdır");
        if (r.purpose() == null || !PURPOSES.contains(r.purpose())) throw ApiException.bad("Təyinat yanlışdır");
    }

    @Transactional
    public Dto.MovementRes createMovement(Dto.MovementReq r) {
        checkMovement(r);
        E.Movement m = new E.Movement();   // deal_id köhnə sahədir, yazılmır; satışa bağlantı r.allocation ilədir
        if (r.allocation() != null && !"IN".equals(r.direction())) throw ApiException.bad("Yalnız mədaxil (IN) satışa bağlana bilər");
        saveMovementRow(m, r);
        if (r.allocation() != null) payments.allocateNew(m, r.allocation());
        return toDto(m, accountMap());
    }

    private Dto.MovementRes saveMovement(E.Movement m, Dto.MovementReq r) {
        saveMovementRow(m, r);
        return toDto(m, accountMap());
    }

    private void saveMovementRow(E.Movement m, Dto.MovementReq r) {
        Long oldAccount = m.accountId;
        m.entryDate = r.date();
        m.accountId = r.accountId();
        m.direction = r.direction();
        m.amount = Money.r2(r.amount());
        m.purpose = r.purpose();
        m.note = blank(r.note());
        m.fileId = r.fileId();
        if (r.statementId() != null) { checkStatement(r.statementId(), r.accountId()); m.statementId = r.statementId(); }
        else if (m.statementId != null && oldAccount != null && !r.accountId().equals(oldAccount)) m.statementId = null;
        movements.save(m);
    }

    private void checkStatement(Long statementId, Long accountId) {
        E.AccountStatement s = statements.findById(statementId).orElseThrow(() -> ApiException.bad("Çıxarış tapılmadı"));
        if (!s.accountId.equals(accountId)) throw ApiException.bad("Çıxarış başqa hesaba aiddir");
    }

    /** Hərəkətin çıxarışını təyin edir/silir (xərcdən yaranan hərəkətlər üçün də) */
    @Transactional
    public Dto.MovementRes setMovementStatement(Long id, Long statementId) {
        E.Movement m = movement(id);
        if (statementId != null) checkStatement(statementId, m.accountId);
        m.statementId = statementId;
        movements.save(m);
        return toDto(m, accountMap());
    }

    private E.Movement movement(Long id) { return movements.findById(id).orElseThrow(() -> ApiException.notFound("Hərəkət tapılmadı")); }

    public E.Movement movementEntity(Long id) { return movement(id); }

    @Transactional
    public Dto.MovementRes updateMovement(Long id, Dto.MovementReq r) {
        E.Movement m = movement(id);
        if (m.expenseId != null) throw ApiException.conflict("xerc_hereketi", "Bu hərəkət xərcdən yaranıb, xərci dəyişin");
        checkMovement(r);
        payments.checkMovementChange(m, r.amount(), r.direction(), r.purpose());
        return saveMovement(m, r);
    }

    @Transactional
    public void deleteMovement(Long id) {
        E.Movement m = movement(id);
        if (m.expenseId != null) throw ApiException.conflict("xerc_hereketi", "Bu hərəkət xərcdən yaranıb, xərci silin");
        payments.checkMovementDelete(id);
        movements.delete(m);
    }

    // ---- xərclər ----
    private Dto.ExpenseRes toDto(E.Expense e) {
        return new Dto.ExpenseRes(e.id, e.entryDate, e.vendor, e.category, e.description, Money.s(e.amount), e.currency,
                Money.rate(e.rate), Money.s(e.amountAzn), e.accountId, e.deductible, e.fileId, e.note, e.itemId, e.docDate,
                movements.findFirstByExpenseId(e.id).map(m -> m.statementId).orElse(null));
    }

    public List<Dto.ExpenseRes> listExpenses(LocalDate from, LocalDate to) {
        return expenses.findAll().stream()
                .filter(e -> from == null || !e.docDate.isBefore(from))
                .filter(e -> to == null || !e.docDate.isAfter(to))
                .sorted(Comparator.comparing((E.Expense e) -> e.docDate).thenComparing(e -> e.entryDate).thenComparing(e -> e.id).reversed())
                .map(this::toDto).toList();
    }

    private E.Expense apply(E.Expense e, Dto.ExpenseReq r) {
        if (r.date() == null) throw ApiException.bad("Tarix göstərilməlidir");
        if (blank(r.category()) == null) throw ApiException.bad("Kateqoriya seçilməlidir");
        if (r.amount() == null || r.amount().signum() <= 0) throw ApiException.bad("Məbləğ sıfırdan böyük olmalıdır");
        if (r.accountId() == null) throw ApiException.bad("Ödənildiyi hesab seçilməlidir");
        account(r.accountId());
        String cur = blank(r.currency()) == null ? "AZN" : r.currency().trim().toUpperCase();
        if (!CURRENCIES.contains(cur)) throw ApiException.bad("Valyuta AZN, USD və ya EUR olmalıdır");
        BigDecimal rate = r.rate() == null || "AZN".equals(cur) && r.rate().signum() <= 0 ? BigDecimal.ONE : r.rate();
        if (rate.signum() <= 0) throw ApiException.bad("Məzənnə sıfırdan böyük olmalıdır");
        e.entryDate = r.date();
        e.docDate = r.docDate() == null ? r.date() : r.docDate();
        e.vendor = blank(r.vendor());
        e.category = r.category().trim();
        e.description = blank(r.description());
        e.amount = Money.r2(r.amount());
        e.currency = cur;
        e.rate = rate;
        // AZN məbləğ = məbləğ × məzənnə; istifadəçi bankın tutduğu real məbləği verə bilər
        e.amountAzn = r.amountAzn() != null && r.amountAzn().signum() > 0 ? Money.r2(r.amountAzn()) : Money.r2(e.amount.multiply(rate));
        e.accountId = r.accountId();
        e.deductible = r.deductible() == null || r.deductible();
        e.fileId = r.fileId();
        e.note = blank(r.note());
        if (r.itemId() != null && !items.existsById(r.itemId())) throw ApiException.bad("Xərc maddəsi tapılmadı");
        e.itemId = r.itemId();
        return expenses.save(e);
    }

    /** Xərcə bağlı OUT hərəkətini yaradır/yeniləyir */
    private void syncMovement(E.Expense e) {
        E.Movement m = movements.findFirstByExpenseId(e.id).orElseGet(E.Movement::new);
        m.expenseId = e.id;
        m.entryDate = e.entryDate;
        if (m.statementId != null && !e.accountId.equals(m.accountId)) m.statementId = null;
        m.accountId = e.accountId;
        m.direction = "OUT";
        m.amount = e.amountAzn;
        m.purpose = "EXPENSE";
        m.note = e.vendor == null ? e.category : e.vendor + " — " + e.category;
        m.fileId = e.fileId;
        movements.save(m);
    }

    @Transactional
    public Dto.ExpenseRes createExpense(Dto.ExpenseReq r) {
        E.Expense e = apply(new E.Expense(), r);
        syncMovement(e);
        return toDto(e);
    }

    @Transactional
    public Dto.ExpenseRes updateExpense(Long id, Dto.ExpenseReq r) {
        E.Expense e = expenses.findById(id).orElseThrow(() -> ApiException.notFound("Xərc tapılmadı"));
        apply(e, r);
        syncMovement(e);
        return toDto(e);
    }

    @Transactional
    public void deleteExpense(Long id) {
        E.Expense e = expenses.findById(id).orElseThrow(() -> ApiException.notFound("Xərc tapılmadı"));
        movements.findFirstByExpenseId(id).ifPresent(movements::delete);
        expenses.delete(e);
    }

    // ---- xərc maddələri ----
    private Dto.ExpenseItemRes toDto(E.ExpenseItem i) {
        String accName = i.accountId == null ? null : accounts.findById(i.accountId).map(a -> a.name).orElse(null);
        return new Dto.ExpenseItemRes(i.id, i.name, i.vendor, i.category, i.currency, i.defaultAmount == null ? null : Money.s(i.defaultAmount),
                i.accountId, accName, i.deductible, i.recurrence, i.active, i.note);
    }

    public List<Dto.ExpenseItemRes> listItems() {
        return items.findAll().stream().sorted(Comparator.comparing((E.ExpenseItem i) -> i.name.toLowerCase())).map(this::toDto).toList();
    }

    private E.ExpenseItem applyItem(E.ExpenseItem i, Dto.ExpenseItemReq r) {
        if (blank(r.name()) == null) throw ApiException.bad("Maddənin adı yazılmalıdır");
        String name = r.name().trim();
        items.findByNameIgnoreCase(name).ifPresent(o -> {
            if (!o.id.equals(i.id)) throw ApiException.conflict("ad_tekrar", "Bu adla xərc maddəsi artıq var");
        });
        String cur = blank(r.currency()) == null ? "AZN" : r.currency().trim().toUpperCase();
        if (!CURRENCIES.contains(cur)) throw ApiException.bad("Valyuta AZN, USD və ya EUR olmalıdır");
        String rec = blank(r.recurrence()) == null ? "NONE" : r.recurrence().trim().toUpperCase();
        if (!rec.equals("MONTHLY") && !rec.equals("NONE")) throw ApiException.bad("Təkrar MONTHLY və ya NONE olmalıdır");
        if (r.defaultAmount() != null && r.defaultAmount().signum() < 0) throw ApiException.bad("Məbləğ mənfi ola bilməz");
        if (r.accountId() != null) account(r.accountId());
        i.name = name;
        i.vendor = blank(r.vendor());
        i.category = blank(r.category());
        i.currency = cur;
        i.defaultAmount = r.defaultAmount() == null ? null : Money.r2(r.defaultAmount());
        i.accountId = r.accountId();
        i.deductible = r.deductible() == null || r.deductible();
        i.recurrence = rec;
        i.active = r.active() == null || r.active();
        i.note = blank(r.note());
        return items.save(i);
    }

    @Transactional
    public Dto.ExpenseItemRes createItem(Dto.ExpenseItemReq r) { return toDto(applyItem(new E.ExpenseItem(), r)); }

    @Transactional
    public Dto.ExpenseItemRes updateItem(Long id, Dto.ExpenseItemReq r) {
        return toDto(applyItem(items.findById(id).orElseThrow(() -> ApiException.notFound("Xərc maddəsi tapılmadı")), r));
    }

    @Transactional
    public void deleteItem(Long id) {
        E.ExpenseItem i = items.findById(id).orElseThrow(() -> ApiException.notFound("Xərc maddəsi tapılmadı"));
        if (expenses.existsByItemId(id))
            throw ApiException.conflict("madde_istifadede", "Bu maddə xərclərdə istifadə olunub, silmək olmaz. Onun əvəzinə passiv edin");
        items.delete(i);
    }

    /** Aktiv MONTHLY maddələr, verilən ayda xərci yazılmayanlar */
    public List<Dto.MissingItem> missingItems(String month) {
        java.time.YearMonth ym;
        try { ym = java.time.YearMonth.parse(month); } catch (Exception ex) { throw ApiException.bad("Ay YYYY-MM formatında olmalıdır"); }
        List<E.Expense> all = expenses.findAll();
        return items.findAll().stream()
                .filter(i -> i.active && "MONTHLY".equals(i.recurrence))
                .filter(i -> all.stream().noneMatch(e -> i.id.equals(e.itemId) && java.time.YearMonth.from(e.docDate).equals(ym)))
                .sorted(Comparator.comparing((E.ExpenseItem i) -> i.name.toLowerCase()))
                .map(i -> {
                    E.Expense last = all.stream().filter(e -> i.id.equals(e.itemId))
                            .max(Comparator.comparing((E.Expense e) -> e.docDate).thenComparing(e -> e.id)).orElse(null);
                    return new Dto.MissingItem(i.id, i.name, i.vendor, i.defaultAmount == null ? null : Money.s(i.defaultAmount), i.currency,
                            last == null ? null : Money.s(last.amountAzn), last == null ? null : last.docDate);
                }).toList();
    }
}
