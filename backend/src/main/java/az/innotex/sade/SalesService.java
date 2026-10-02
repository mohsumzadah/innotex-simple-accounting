package az.innotex.sade;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Müştərilər, satışlar (lisenziya) və mərhələ keçidləri */
@Service
public class SalesService {
    /** Mərhələlər (istənilən ardıcıllıqla dəyişə bilər; PROTOCOL və ADVANCE_INVOICE istəyə bağlıdır) */
    public static final List<String> STAGES = List.of("NEW", "CONTRACT", "PROTOCOL", "ADVANCE_INVOICE", "PAID", "IN_PROGRESS", "ACT", "INVOICE", "DONE");

    private final R.Customers customers;
    private final R.Deals deals;
    private final R.Events events;
    private final R.DealDocs dealDocs;
    private final R.Products products;
    private final PaymentService payments;
    private final Numbering numbering;
    private final DocService docService;
    private final FileService fileSvc;
    private final LedgerService ledger;

    public SalesService(R.Customers customers, R.Deals deals, R.Events events, R.DealDocs dealDocs, R.Products products, PaymentService payments, Numbering numbering, DocService docService, FileService fileSvc, LedgerService ledger) {
        this.ledger = ledger;
        this.fileSvc = fileSvc;
        this.numbering = numbering;
        this.docService = docService;
        this.products = products;
        this.payments = payments;
        this.customers = customers;
        this.deals = deals;
        this.events = events;
        this.dealDocs = dealDocs;
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    // ---- müştərilər ----
    private static Dto.CustomerDto toDto(E.Customer c) {
        return new Dto.CustomerDto(c.id, c.name, c.voen, c.kind, c.address, c.director, c.bank, c.iban, c.bankCode, c.bankVoen, c.swift, c.correspondentAccount, c.phone, c.email, c.note, c.fin, c.entityType, c.cardNumber);
    }

    E.Customer customer(Long id) { return customers.findById(id).orElseThrow(() -> ApiException.notFound("Kontragent tapılmadı")); }

    public List<Dto.CustomerDto> listCustomers() {
        return customers.findAll().stream().sorted(Comparator.comparing(c -> c.name.toLowerCase())).map(SalesService::toDto).toList();
    }

    private E.Customer apply(E.Customer c, Dto.CustomerDto r) {
        if (blank(r.name()) == null) throw ApiException.bad("Kontragentin adı boş ola bilməz");
        c.name = r.name().trim(); c.voen = blank(r.voen()); c.address = blank(r.address()); c.director = blank(r.director());
        c.bank = blank(r.bank()); c.iban = blank(r.iban()); c.bankCode = blank(r.bankCode()); c.swift = blank(r.swift());
        c.phone = blank(r.phone()); c.email = blank(r.email());
        c.bankVoen = blank(r.bankVoen()); c.correspondentAccount = blank(r.correspondentAccount()); c.note = blank(r.note());
        c.fin = blank(r.fin()); c.cardNumber = blank(r.cardNumber());
        String kind = blank(r.kind()) == null ? "CUSTOMER" : r.kind().trim().toUpperCase();
        String type = blank(r.entityType()) == null ? null : r.entityType().trim().toUpperCase();
        if ("INDIVIDUAL".equals(kind)) {   // köhnə müqavilə: kind=INDIVIDUAL → entityType=INDIVIDUAL, rol CUSTOMER
            kind = "CUSTOMER";
            if (type == null) type = "INDIVIDUAL";
        }
        if (type == null) type = "LEGAL";
        if (!java.util.Set.of("CUSTOMER", "SUPPLIER", "BOTH").contains(kind)) throw ApiException.bad("Rol CUSTOMER, SUPPLIER və ya BOTH olmalıdır");
        if (!java.util.Set.of("LEGAL", "INDIVIDUAL").contains(type)) throw ApiException.bad("Şəxs növü LEGAL və ya INDIVIDUAL olmalıdır");
        c.kind = kind; c.entityType = type;
        if ("INDIVIDUAL".equals(type)) {   // fiziki şəxs: VÖEN məcburi deyil; direktor, bank kodu/VÖEN, SWIFT, müxbir hesab saxlanmır (IBAN və kart qalır)
            c.bank = null; c.bankCode = null; c.swift = null; c.bankVoen = null; c.correspondentAccount = null; c.director = null;
        } else {
            c.fin = null; c.cardNumber = null;
        }
        return customers.save(c);
    }

    @Transactional
    public Dto.CustomerDto createCustomer(Dto.CustomerDto r) { return toDto(apply(new E.Customer(), r)); }

    @Transactional
    public Dto.CustomerDto updateCustomer(Long id, Dto.CustomerDto r) { return toDto(apply(customer(id), r)); }

    @Transactional
    public void deleteCustomer(Long id) {
        E.Customer c = customer(id);
        if (deals.existsByCustomerId(id)) throw ApiException.conflict("musteri_istifade_olunub", "Kontragentin satışları var, silmək olmaz");
        customers.delete(c);
    }

    // ---- nomenklatura (məhsul/xidmət kataloqu) ----
    private static Dto.ProductRes toDto(E.Product p) {
        return new Dto.ProductRes(p.id, p.name, p.code, p.unit, p.defaultPrice == null ? null : Money.s(p.defaultPrice), p.defaultComputers, p.description, p.active,
                p.defaultPaymentTerms, p.defaultAdvancePercent == null ? null : Money.rate(p.defaultAdvancePercent));
    }

    E.Product product(Long id) { return products.findById(id).orElseThrow(() -> ApiException.notFound("Məhsul tapılmadı")); }

    public List<Dto.ProductRes> listProducts(Boolean active) {
        return products.findAll().stream().filter(p -> active == null || p.active == active)
                .sorted(Comparator.comparing((E.Product p) -> p.name.toLowerCase())).map(SalesService::toDto).toList();
    }

    private E.Product apply(E.Product p, Dto.ProductReq r) {
        if (blank(r.name()) == null) throw ApiException.bad("Məhsulun adı yazılmalıdır");
        String name = r.name().trim();
        products.findByNameIgnoreCase(name).ifPresent(o -> {
            if (!o.id.equals(p.id)) throw ApiException.conflict("ad_tekrar", "Bu adla məhsul artıq var");
        });
        if (r.defaultPrice() != null && r.defaultPrice().signum() < 0) throw ApiException.bad("Qiymət mənfi ola bilməz");
        if (r.defaultComputers() != null && r.defaultComputers() < 1) throw ApiException.bad("Kompüter sayı ən azı 1 olmalıdır");
        p.name = name;
        p.code = blank(r.code());
        p.unit = blank(r.unit()) == null ? "ədəd" : r.unit().trim();
        p.defaultPrice = r.defaultPrice() == null ? null : Money.r2(r.defaultPrice());
        p.defaultComputers = r.defaultComputers();
        p.description = blank(r.description());
        String t = blank(r.defaultPaymentTerms()) == null ? null : r.defaultPaymentTerms().trim().toUpperCase();
        if (t == null) {
            p.defaultPaymentTerms = null;
            p.defaultAdvancePercent = null;
        } else {
            if (!PaymentService.TERMS.contains(t) || "RETAIL".equals(t)) throw ApiException.bad("Ödəniş şərti PREPAID, PARTIAL və ya POSTPAID olmalıdır");
            BigDecimal pc = r.defaultAdvancePercent();
            switch (t) {
                case "PREPAID" -> pc = Money.HUNDRED;
                case "POSTPAID" -> pc = BigDecimal.ZERO;
                default -> {
                    if (pc == null || pc.compareTo(BigDecimal.ONE) < 0 || pc.compareTo(new BigDecimal("99")) > 0)
                        throw ApiException.bad("Qismən avans faizi 1 ilə 99 arasında olmalıdır");
                }
            }
            p.defaultPaymentTerms = t;
            p.defaultAdvancePercent = pc.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        p.active = r.active() == null || r.active();
        return products.save(p);
    }

    @Transactional
    public Dto.ProductRes createProduct(Dto.ProductReq r) { return toDto(apply(new E.Product(), r)); }

    @Transactional
    public Dto.ProductRes updateProduct(Long id, Dto.ProductReq r) { return toDto(apply(product(id), r)); }

    @Transactional
    public void deleteProduct(Long id) {
        E.Product p = product(id);
        if (deals.existsByProductId(id))
            throw ApiException.conflict("mehsul_istifade_olunub", "Bu məhsul satışlarda istifadə olunub, silmək olmaz. Onun əvəzinə passiv edin");
        products.delete(p);
    }

    // ---- satışlar ----
    private Dto.DealRes toDto(E.Deal d, Map<Long, E.Customer> cs, List<E.PaymentAllocation> al, LocalDate since) {
        E.Customer c = cs.get(d.customerId);
        PaymentService.Summary ps = PaymentService.summary(d, al);
        E.Product pr = d.productId == null ? null : products.findById(d.productId).orElse(null);
        return new Dto.DealRes(d.id, d.customerId, c == null ? null : c.name, d.productId, pr == null ? null : pr.code, d.product, d.description, Money.s(d.price), d.computers, d.stage,
                d.contractNo, d.contractDate, d.protocolDate, d.advanceInvoiceNo, d.advanceInvoiceDate, Money.s(d.advanceAmount), d.advanceFileId,
                d.paymentDate, Money.s(d.paymentAmount), d.paymentFileId, d.paymentMovementId, d.actNo, d.actDate, d.actFileId,
                d.invoiceNo, d.invoiceDate, Money.s(d.invoiceAmount), d.invoiceFileId, d.note, d.createdAt.toString(), d.saleDate,
                d.paymentTerms, Money.rate(d.advancePercent), Money.s(ps.advanceRequired()), Money.s(ps.advancePaid()), Money.s(ps.paid()), Money.s(ps.remaining()),
                ps.status(), !d.cancelled && PaymentService.awaiting(d, ps), PaymentService.stepper(d, ps,
                        dealDocs.findByDealIdOrderByIdAsc(d.id).stream().map(x -> x.code).collect(Collectors.toSet())), since,
                d.cancelled, d.cancelReason, d.cancelledAt == null ? null : d.cancelledAt.toString(), !al.isEmpty(), d.quantity);
    }

    private Dto.DealRes toDto(E.Deal d, Map<Long, E.Customer> cs) {
        return toDto(d, cs, payments.forDealRaw(d.id), stageSince(d, events.findByDealIdOrderByIdDesc(d.id)));
    }

    /** Cari mərhələyə keçid tarixi (ən son hadisə; yoxdursa satış tarixi) */
    private static LocalDate stageSince(E.Deal d, List<E.DealEvent> evs) {
        for (E.DealEvent e : evs) if (d.stage.equals(e.toStage)) return e.eventDate != null ? e.eventDate : e.createdAt.toLocalDate();
        return d.saleDate;
    }

    private Map<Long, E.Customer> customerMap() { return customers.findAll().stream().collect(Collectors.toMap(c -> c.id, Function.identity())); }

    E.Deal deal(Long id) { return deals.findById(id).orElseThrow(() -> ApiException.notFound("Satış tapılmadı")); }

    public List<Dto.DealRes> listDeals(String stage, boolean includeCancelled) {
        Map<Long, E.Customer> cs = customerMap();
        Map<Long, List<E.PaymentAllocation>> al = payments.byDeal();
        Map<Long, List<E.DealEvent>> evs = events.findAll().stream().sorted(Comparator.comparing((E.DealEvent e) -> e.id).reversed()).collect(Collectors.groupingBy(e -> e.dealId));
        return deals.findAll().stream().filter(d -> includeCancelled || !d.cancelled)
                .filter(d -> stage == null || stage.isBlank() || stage.equals(d.stage))
                .sorted(Comparator.comparing((E.Deal d) -> d.saleDate).thenComparing(d -> d.id).reversed())
                .map(d -> toDto(d, cs, al.getOrDefault(d.id, List.of()), stageSince(d, evs.getOrDefault(d.id, List.of())))).toList();
    }

    public Dto.DealRes getDeal(Long id) { return toDto(deal(id), customerMap()); }

    private void event(Long dealId, String from, String to, String note, LocalDate date) {
        E.DealEvent e = new E.DealEvent();
        e.dealId = dealId;
        e.createdAt = LocalDateTime.now();
        e.eventDate = date == null ? LocalDate.now() : date;
        e.fromStage = from;
        e.toStage = to;
        e.note = blank(note);
        events.save(e);
    }

    /** Ödəniş şərti: PREPAID=100%, POSTPAID=0%, PARTIAL=1..99 (istifadəçi yazır) */
    private void applyTerms(E.Deal d, String terms, BigDecimal percent) {
        String t = blank(terms) == null ? d.paymentTerms : terms.trim().toUpperCase();
        if (!PaymentService.TERMS.contains(t)) throw ApiException.bad("Ödəniş şərti PREPAID, PARTIAL, POSTPAID və ya RETAIL olmalıdır");
        BigDecimal p = percent != null ? percent : d.advancePercent;
        switch (t) {
            case "PREPAID" -> p = Money.HUNDRED;
            case "POSTPAID", "RETAIL" -> p = BigDecimal.ZERO;
            default -> {
                if (p == null || p.compareTo(BigDecimal.ONE) < 0 || p.compareTo(new BigDecimal("99")) > 0)
                    throw ApiException.bad("Qismən avans faizi 1 ilə 99 arasında olmalıdır");
            }
        }
        d.paymentTerms = t;
        d.advancePercent = p.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    @Transactional
    public Dto.DealRes createDeal(Dto.DealReq r) {
        if (r.customerId() == null) throw ApiException.bad("Müştəri seçilməlidir");
        customer(r.customerId());
        if (r.price() == null || r.price().signum() < 0) throw ApiException.bad("Qiymət göstərilməlidir");
        E.Deal d = new E.Deal();
        d.customerId = r.customerId();
        if (r.productId() != null && r.productId() > 0) {
            E.Product pr = product(r.productId());
            d.productId = pr.id;
            d.product = pr.name;
        } else {
            d.product = blank(r.product()) == null ? "INNOTEX e-Qaimə" : r.product().trim();
        }
        d.description = blank(r.description());
        d.price = Money.r2(r.price());
        d.computers = r.computers() == null || r.computers() < 1 ? 1 : r.computers();
        if (r.quantity() != null && r.quantity() < 1) throw ApiException.bad("Miqdar ən azı 1 olmalıdır");
        d.quantity = r.quantity() == null ? 1 : r.quantity();
        d.stage = "NEW";
        d.paymentTerms = "POSTPAID";
        d.advancePercent = BigDecimal.ZERO;
        applyTerms(d, r.paymentTerms(), r.advancePercent());
        d.note = blank(r.note());
        d.saleDate = r.saleDate() == null ? LocalDate.now() : r.saleDate();
        d.createdAt = LocalDateTime.now();
        d = deals.save(d);
        event(d.id, null, "NEW", "Satış yaradıldı", d.saleDate);
        if (Boolean.TRUE.equals(r.startContract()) && !"RETAIL".equals(d.paymentTerms)) {   // fiziki şəxsə müqavilə nömrəsi verilmir
            // eyni tranzaksiyada: müqavilə nömrəsi + tarixi (= satış tarixi), mərhələ CONTRACT, əsas şablondan sənəd
            d.contractDate = d.saleDate;
            d.contractNo = nextContractNo(d.contractDate.getYear());
            d.stage = "CONTRACT";
            d = deals.save(d);
            event(d.id, "NEW", "CONTRACT", "Müqavilə hazırlandı", d.saleDate);
            docService.create(d.id, new Dto.DealDocCreateReq("CONTRACT", null));
        }
        return toDto(d, customerMap());
    }

    @Transactional
    public Dto.DealRes updateDeal(Long id, Dto.DealReq r) {
        E.Deal d = deal(id);
        if (r.customerId() != null) { customer(r.customerId()); d.customerId = r.customerId(); }
        if (r.productId() != null) d.productId = r.productId() <= 0 ? null : product(r.productId()).id;   // 0 = bağı sil
        if (blank(r.product()) != null) d.product = r.product().trim();
        if (r.description() != null) d.description = blank(r.description());
        if (r.price() != null) {
            if (r.price().signum() < 0) throw ApiException.bad("Qiymət mənfi ola bilməz");
            d.price = Money.r2(r.price());
        }
        if (r.computers() != null) {
            if (r.computers() < 1) throw ApiException.bad("Kompüter sayı ən azı 1 olmalıdır");
            d.computers = r.computers();
        }
        if (r.quantity() != null) {
            if (r.quantity() < 1) throw ApiException.bad("Miqdar ən azı 1 olmalıdır");
            d.quantity = r.quantity();
        }
        if (r.saleDate() != null) d.saleDate = r.saleDate();
        if (r.note() != null) d.note = blank(r.note());
        if (r.paymentTerms() != null || r.advancePercent() != null) applyTerms(d, r.paymentTerms(), r.advancePercent());
        return toDto(deals.save(d), customerMap());
    }

    /** Satışı silir (istənilən mərhələdə); ödəniş bağlantısı varsa 409. Sənədləri, tarixçəsi və yalnız ona aid fayllar silinir, bank hərəkətinə toxunulmur. */
    @Transactional
    public void deleteDeal(Long id) {
        E.Deal d = deal(id);
        if (!payments.forDealRaw(id).isEmpty()) throw ApiException.conflict("odenis_bagli", "Əvvəlcə ödəniş bağlantılarını ayırın");
        java.util.LinkedHashSet<Long> fileIds = new java.util.LinkedHashSet<>();
        for (Long f : new Long[]{d.advanceFileId, d.actFileId, d.invoiceFileId, d.paymentFileId}) if (f != null) fileIds.add(f);
        for (E.DealDocument x : dealDocs.findByDealIdOrderByIdAsc(id)) {
            if (x.fileId != null) fileIds.add(x.fileId);
            if (x.previousFileId != null) fileIds.add(x.previousFileId);
        }
        dealDocs.deleteByDealId(id);
        events.deleteByDealId(id);
        deals.delete(d);
        deals.flush();
        for (Long f : fileIds) if (!fileSvc.isReferenced(f)) fileSvc.delete(f);
    }

    @Transactional
    public Dto.DealRes cancel(Long id, String reason) {
        E.Deal d = deal(id);
        if (d.cancelled) throw ApiException.conflict("artiq_legv", "Satış artıq ləğv olunub");
        d.cancelled = true;
        d.cancelReason = blank(reason);
        d.cancelledAt = LocalDateTime.now();
        return toDto(deals.save(d), customerMap());
    }

    @Transactional
    public Dto.DealRes restore(Long id) {
        E.Deal d = deal(id);
        if (!d.cancelled) throw ApiException.conflict("legv_deyil", "Satış ləğv olunmayıb");
        d.cancelled = false;
        d.cancelReason = null;
        d.cancelledAt = null;
        return toDto(deals.save(d), customerMap());
    }

    private String nextContractNo(int year) { return numbering.nextContractNo(year); }

    /** Mərhələ sahələri (göndərilənlər yazılır, boşlar toxunulmaz qalır) */
    private void applyFields(E.Deal d, Dto.StageReq r) {
        // mərhələ sahələri (göndərilənlər yazılır, boşlar toxunulmaz qalır)
        if (blank(r.contractNo()) != null) d.contractNo = r.contractNo().trim();
        if (r.contractDate() != null) d.contractDate = r.contractDate();
        if (r.protocolDate() != null) d.protocolDate = r.protocolDate();
        if (blank(r.advanceInvoiceNo()) != null) d.advanceInvoiceNo = r.advanceInvoiceNo().trim();
        if (r.advanceInvoiceDate() != null) d.advanceInvoiceDate = r.advanceInvoiceDate();
        if (r.advanceAmount() != null) d.advanceAmount = Money.r2(r.advanceAmount());
        if (r.advanceFileId() != null) d.advanceFileId = r.advanceFileId();
        if (blank(r.actNo()) != null) d.actNo = r.actNo().trim();
        if (r.actDate() != null) d.actDate = r.actDate();
        if (r.actFileId() != null) d.actFileId = r.actFileId();
        if (blank(r.invoiceNo()) != null) d.invoiceNo = r.invoiceNo().trim();
        if (r.invoiceDate() != null) d.invoiceDate = r.invoiceDate();
        if (r.invoiceAmount() != null) d.invoiceAmount = Money.r2(r.invoiceAmount());
        if (r.invoiceFileId() != null) d.invoiceFileId = r.invoiceFileId();

    }

    /** Mərhələnin boş sahələrinin default-ları (today = mərhələ tarixi) */
    private void stageDefaults(E.Deal d, String stage, LocalDate today) {
        switch (stage) {
            case "CONTRACT" -> {
                if (d.contractDate == null) d.contractDate = today;
                if (d.contractNo == null) d.contractNo = nextContractNo(d.contractDate.getYear());
            }
            case "PROTOCOL" -> { if (d.protocolDate == null) d.protocolDate = today; }
            case "ADVANCE_INVOICE" -> {
                if (d.advanceInvoiceDate == null) d.advanceInvoiceDate = today;
                if (d.advanceAmount == null) {   // default: qiymət × avans faizi / 100 (redaktə oluna bilər)
                    BigDecimal adv = PaymentService.percentOf(d.price, d.advancePercent);
                    if (adv.signum() > 0) d.advanceAmount = adv;
                }
            }
            // PAID: göstəricidir, hərəkət yaratmır; ödənişlər "Ödənişlər" kartından bank mədaxilinə bağlanır
            case "ACT" -> {
                if (d.actDate == null) d.actDate = today;
                if (d.actNo == null) d.actNo = numbering.nextActNo(d.actDate.getYear());   // AKT-YYYY-NNN, redaktə oluna bilər
            }
            case "INVOICE" -> {
                if (d.invoiceDate == null) d.invoiceDate = today;
                if (d.invoiceAmount == null) d.invoiceAmount = d.price;
            }
            default -> { }
        }
    }

    private static final Set<String> OPTIONAL = Set.of("PROTOCOL", "ADVANCE_INVOICE");

    /** Stepper ardıcıllığına görə növbəti real mərhələ (FINAL_PAYMENT göstəricidir, mərhələ deyil) */
    private String nextStage(E.Deal d) {
        PaymentService.Summary ps = PaymentService.summary(d, payments.forDealRaw(d.id));
        List<String> keys = PaymentService.stepper(d, ps, dealDocs.findByDealIdOrderByIdAsc(d.id).stream().map(x -> x.code).collect(Collectors.toSet()))
                .stream().map(Dto.StepRes::key).filter(STAGES::contains).toList();
        int i = keys.indexOf(d.stage);
        if (i < 0) {
            int j = STAGES.indexOf(d.stage);
            return j + 1 < STAGES.size() ? STAGES.get(j + 1) : null;
        }
        return i + 1 < keys.size() ? keys.get(i + 1) : null;
    }

    /** Cari mərhələnin sahələrini yazır və stepper ardıcıllığındakı növbəti mərhələyə keçirir */
    @Transactional
    public Dto.DealRes complete(Long id, Dto.StageReq r) {
        E.Deal d = deal(id);
        String next = nextStage(d);
        if (next == null) throw ApiException.bad("Satış artıq son mərhələdədir");
        LocalDate date = r.eventDate() == null ? LocalDate.now() : r.eventDate();
        applyFields(d, r);
        stageDefaults(d, d.stage, date);
        stageDefaults(d, next, date);
        String old = d.stage;
        d.stage = next;
        deals.save(d);
        event(d.id, old, next, r.note(), date);
        return toDto(d, customerMap());
    }

    /** İstəyə bağlı cari mərhələni məlumatsız ötürür */
    @Transactional
    public Dto.DealRes skip(Long id, Dto.StageReq r) {
        E.Deal d = deal(id);
        boolean retailInstall = "RETAIL".equals(d.paymentTerms) && "IN_PROGRESS".equals(d.stage);   // fiziki şəxs: quraşdırma istəyə bağlıdır
        if (!OPTIONAL.contains(d.stage) && !retailInstall) throw ApiException.bad("Yalnız istəyə bağlı mərhələ ötürülə bilər");
        String next = nextStage(d);
        if (next == null) throw ApiException.bad("Satış artıq son mərhələdədir");
        LocalDate date = r == null || r.eventDate() == null ? LocalDate.now() : r.eventDate();
        stageDefaults(d, next, date);
        String old = d.stage;
        d.stage = next;
        deals.save(d);
        String note = r != null && blank(r.note()) != null ? r.note().trim() : "Ötürüldü";
        event(d.id, old, next, note, date);
        return toDto(d, customerMap());
    }

    @Transactional
    public Dto.DealRes changeStage(Long id, Dto.StageReq r) {
        E.Deal d = deal(id);
        if (r.stage() == null || !STAGES.contains(r.stage())) throw ApiException.bad("Mərhələ yanlışdır");
        checkRetailStage(d, r.stage());
        String old = d.stage;
        LocalDate today = r.eventDate() == null ? LocalDate.now() : r.eventDate();   // mərhələ tarixi: boş sahələrin default-u və hadisə tarixi
        applyFields(d, r);
        stageDefaults(d, r.stage(), today);
        d.stage = r.stage();
        deals.save(d);
        if (!r.stage().equals(old)) event(d.id, old, r.stage(), r.note(), r.eventDate());
        else touchEvent(d, r, true);
        return toDto(d, customerMap());
    }

    /** Mərhələnin son qeydinin tarixi düzəldilir, qeyd əlavə olunur (create: qeyd yoxdursa yaradılsın) */
    private void touchEvent(E.Deal d, Dto.StageReq r, boolean create) {
        String st = r.stage();
        E.DealEvent last = events.findByDealIdOrderByIdDesc(d.id).stream().filter(e -> st.equals(e.toStage)).findFirst().orElse(null);
        if (last == null) {
            if (create) event(d.id, null, st, r.note(), r.eventDate());
            return;
        }
        if (r.eventDate() != null) last.eventDate = r.eventDate();
        String n = blank(r.note());
        if (n != null) last.note = last.note == null ? n : last.note + "; " + n;
        events.save(last);
    }

    /** Mərhələni dəyişmədən verilən mərhələnin (boşdursa cari) sahələrini yadda saxlayır */
    @Transactional
    public Dto.DealRes saveFields(Long id, Dto.StageReq r) {
        E.Deal d = deal(id);
        String st = blank(r.stage()) == null ? d.stage : r.stage();
        if (!STAGES.contains(st)) throw ApiException.bad("Mərhələ yanlışdır");
        checkRetailStage(d, st);
        applyFields(d, r);
        stageDefaults(d, st, r.eventDate() == null ? LocalDate.now() : r.eventDate());
        deals.save(d);
        touchEvent(d, new Dto.StageReq(st, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, r.note(), r.eventDate()), st.equals(d.stage));
        return toDto(d, customerMap());
    }

    /** Fiziki şəxs (RETAIL) satışında müqavilə/protokol/avans/akt/qaimə mərhələləri yoxdur */
    private static void checkRetailStage(E.Deal d, String stage) {
        if ("RETAIL".equals(d.paymentTerms) && !Set.of("NEW", "PAID", "IN_PROGRESS", "DONE").contains(stage))
            throw ApiException.bad("Fiziki şəxs satışında bu mərhələ yoxdur (Yeni → Ödəniş və çek → Quraşdırma → Tamamlandı)");
    }

    private static final Set<String> RECEIPT_TYPES = Set.of("image/png", "image/jpeg", "application/pdf");

    /**
     * Sürətli satış (fiziki şəxs): kontragent (lazımsa yeni), RETAIL satış, ödəniş bağlantısı (FINAL, fayl ilə) və mərhələ
     * (quraşdırılıbsa DONE, yoxsa PAID) bir tranzaksiyada. Tarixçə: NEW (satış tarixi) → PAID → DONE (ikisi də max(satış tarixi, ödəniş tarixi)).
     */
    @Transactional
    public Dto.DealRes quickRetail(Dto.QuickRetailReq r, org.springframework.web.multipart.MultipartFile file) {
        if (r.price() == null || r.price().signum() <= 0) throw ApiException.bad("Qiymət sıfırdan böyük olmalıdır");
        if (r.movementId() == null && r.movement() == null) throw ApiException.bad("Ödəniş üçün mövcud bank mədaxili seçin və ya yeni mədaxil yazın");
        if (r.movementId() != null && r.movement() != null) throw ApiException.bad("Ya mövcud mədaxil, ya da yeni mədaxil göstərin");
        if (r.customerId() == null && (r.buyer() == null || blank(r.buyer().name()) == null)) throw ApiException.bad("Alıcı seçilməli və ya adı yazılmalıdır");
        if (file != null && !file.isEmpty()) {
            String ct = file.getContentType() == null ? "" : file.getContentType().toLowerCase(java.util.Locale.ROOT);
            if (!RECEIPT_TYPES.contains(ct) && !RECEIPT_TYPES.contains(FileService.typeByName(file.getOriginalFilename())))
                throw ApiException.bad("Çek yalnız şəkil (PNG, JPEG) və ya PDF ola bilər");
        }
        // alıcı
        Long customerId = r.customerId();
        if (customerId != null) {
            if (!"INDIVIDUAL".equals(customer(customerId).entityType)) throw ApiException.bad("Sürətli satış yalnız fiziki şəxs kontragentinə edilir");
        } else {
            Dto.QuickBuyer b = r.buyer();
            customerId = createCustomer(new Dto.CustomerDto(null, b.name(), null, "CUSTOMER", null, null, null, null, null, null, null, null, b.phone(), b.email(), null, b.fin(), "INDIVIDUAL", null)).id();
        }
        // ödəniş mədaxili
        Long movementId = r.movementId();
        LocalDate payDate;
        Long fileId = null;
        if (file != null && !file.isEmpty()) fileId = fileSvc.store(file).id();
        if (movementId != null) {
            E.Movement m = ledger.movementEntity(movementId);
            payDate = m.entryDate;
        } else {
            Dto.QuickMovement qm = r.movement();
            payDate = qm.date();
            movementId = ledger.createMovement(new Dto.MovementReq(qm.date(), qm.accountId(), "IN", qm.amount(), "CUSTOMER_PAYMENT", qm.note(), fileId, null, null, null)).id();
        }
        LocalDate saleDate = r.saleDate() == null ? LocalDate.now() : r.saleDate();
        LocalDate stageDate = payDate != null && payDate.isAfter(saleDate) ? payDate : saleDate;
        Dto.DealRes created = createDeal(new Dto.DealReq(customerId, r.productId(), r.product(), null, r.price(), 1, r.note(), saleDate, "RETAIL", null, false, 1));
        payments.create(created.id(), new Dto.PaymentReq(movementId, null, "FINAL", fileId, null, null));
        E.Deal d = deal(created.id());
        boolean installed = r.installed() == null || r.installed();
        d.stage = "PAID";
        deals.save(d);
        event(d.id, "NEW", "PAID", "Ödəniş alındı", stageDate);
        if (installed) {
            d.stage = "DONE";
            deals.save(d);
            event(d.id, "PAID", "DONE", "Satış tamamlandı", stageDate);
        }
        return toDto(d, customerMap());
    }

    public List<Dto.EventRes> events(Long id) {
        deal(id);
        return events.findByDealIdOrderByIdDesc(id).stream()
                .map(e -> new Dto.EventRes(e.id, e.eventDate, e.fromStage, e.toStage, e.note, e.createdAt.toString())).toList();
    }
}
