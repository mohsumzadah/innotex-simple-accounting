package az.innotex.sade;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Satış ödənişləri: ödəniş şərtləri (PREPAID/PARTIAL/POSTPAID), bank mədaxilinin satışlara bağlanması (payment_allocation)
 * və satışın ödəniş xülasəsi. Köhnə deal.payment_* sahələri və movement.deal_id məntiqdə istifadə olunmur.
 */
@Service
public class PaymentService {
    public static final Set<String> TERMS = Set.of("PREPAID", "PARTIAL", "POSTPAID", "RETAIL");
    static final Set<String> KINDS = Set.of("ADVANCE", "FINAL", "OTHER");
    /** Satışa yalnız "Müştəri ödənişi" təyinatlı mədaxil bağlanır */
    static final Set<String> ALLOCATABLE_PURPOSES = Set.of("CUSTOMER_PAYMENT");

    private final R.Deals deals;
    private final R.Movements movements;
    private final R.Allocations allocs;
    private final R.Accounts accounts;
    private final R.Customers customers;
    private final R.Files files;

    public PaymentService(R.Deals deals, R.Movements movements, R.Allocations allocs, R.Accounts accounts, R.Customers customers, R.Files files) {
        this.deals = deals;
        this.movements = movements;
        this.allocs = allocs;
        this.accounts = accounts;
        this.customers = customers;
        this.files = files;
    }

    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    // ---- xülasə (saf hesablama) ----
    public record Summary(BigDecimal price, BigDecimal paid, BigDecimal remaining, String status, BigDecimal advanceRequired, BigDecimal advancePaid) {}

    /** Tələb olunan avans: POSTPAID = 0; əks halda advance_amount (verilibsə) və ya qiymət × faiz / 100 */
    public static BigDecimal advanceRequired(E.Deal d) {
        if ("POSTPAID".equals(d.paymentTerms) || "RETAIL".equals(d.paymentTerms)) return Money.ZERO;
        if (d.advanceAmount != null && d.advanceAmount.signum() > 0) return Money.r2(d.advanceAmount);
        return percentOf(d.price, d.advancePercent);
    }

    public static BigDecimal percentOf(BigDecimal price, BigDecimal percent) {
        return Money.r2(Money.nz(price).multiply(Money.nz(percent)).divide(Money.HUNDRED, 4, java.math.RoundingMode.HALF_UP));
    }

    public static Summary summary(E.Deal d, Collection<E.PaymentAllocation> list) {
        BigDecimal paid = Money.ZERO, adv = Money.ZERO;
        for (E.PaymentAllocation a : list) {
            paid = paid.add(a.amount);
            if ("ADVANCE".equals(a.kind)) adv = adv.add(a.amount);
        }
        paid = Money.r2(paid);
        BigDecimal price = Money.r2(d.price);
        int c = paid.compareTo(price);
        String status = paid.signum() == 0 && price.signum() > 0 ? "UNPAID" : c < 0 ? "PARTIAL" : c == 0 ? "PAID" : "OVERPAID";
        return new Summary(price, paid, price.subtract(paid).max(Money.ZERO), status, advanceRequired(d), Money.r2(adv));
    }

    private static boolean advanceDone(Summary s) { return s.advancePaid().compareTo(s.advanceRequired()) >= 0; }

    /** "Ödəniş gözlənilir": qalıq var və (qaimə/tamamlanıb, yaxud avans tələb olunur, avans qaiməsi mərhələsinə çatılıb və ödənilməyib) */
    public static boolean awaiting(E.Deal d, Summary s) {
        if (s.remaining().signum() <= 0) return false;
        if ("RETAIL".equals(d.paymentTerms)) return true;   // fiziki şəxs: ödəniş birinci addımdır
        if ("INVOICE".equals(d.stage) || "DONE".equals(d.stage)) return true;
        boolean issued = d.advanceInvoiceNo != null || !List.of("NEW", "CONTRACT", "PROTOCOL").contains(d.stage);
        return s.advanceRequired().signum() > 0 && !advanceDone(s) && issued;
    }

    private static final Map<String, String> LABEL = Map.of("NEW", "Yeni müştəri", "CONTRACT", "Müqavilə", "PROTOCOL", "Qiymət protokolu",
            "ADVANCE_INVOICE", "Avans qaiməsi", "IN_PROGRESS", "İcra", "ACT", "Təhvil-təslim aktı", "INVOICE", "Qaimə", "DONE", "Tamamlandı");

    /** Ödəniş şərtinə görə hesablanmış mərhələ zolağı; ödəniş addımları göstəricidir (avtomatik "done") */
    public static List<Dto.StepRes> stepper(E.Deal d, Summary s) { return stepper(d, s, Set.of()); }

    /**
     * Addım ya satış ondan irəli keçibsə, ya da sübutu varsa (nömrə, tarix, fayl və ya satışa yüklənmiş sənəd) "done" olur.
     * docCodes: satışın sənədlərinin kodları (CONTRACT, PROTOCOL, ACT).
     */
    public static List<Dto.StepRes> stepper(E.Deal d, Summary s, Set<String> docCodes) {
        String terms = TERMS.contains(d.paymentTerms) ? d.paymentTerms : "POSTPAID";
        if ("RETAIL".equals(terms)) return retailStepper(d, s);
        List<String> keys = new ArrayList<>(List.of("NEW", "CONTRACT", "PROTOCOL"));
        switch (terms) {
            case "PREPAID" -> keys.addAll(List.of("ADVANCE_INVOICE", "PAID", "IN_PROGRESS", "ACT", "INVOICE", "DONE"));
            case "PARTIAL" -> keys.addAll(List.of("ADVANCE_INVOICE", "PAID", "IN_PROGRESS", "ACT", "INVOICE", "FINAL_PAYMENT", "DONE"));
            default -> {
                if ("ADVANCE_INVOICE".equals(d.stage)) keys.add("ADVANCE_INVOICE");   // sərbəst keçid: zolaqda itməsin
                keys.addAll(List.of("IN_PROGRESS", "ACT", "INVOICE", "PAID", "DONE"));
            }
        }
        int cur = keys.indexOf(d.stage);
        List<Dto.StepRes> out = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            String label;
            boolean done;
            if (k.equals("PAID")) {
                label = switch (terms) { case "PREPAID" -> "Ödəniş alındı"; case "PARTIAL" -> "Avans ödənişi"; default -> "Ödəniş"; };
                done = "POSTPAID".equals(terms) ? s.paid().compareTo(s.price()) >= 0 : advanceDone(s);
            } else if (k.equals("FINAL_PAYMENT")) {
                label = "Qalıq ödəniş";
                done = s.paid().compareTo(s.price()) >= 0;
            } else {
                label = LABEL.get(k);
                done = i < cur || evidence(k, d, docCodes);
            }
            out.add(new Dto.StepRes(k, label, k.equals("PROTOCOL") || k.equals("ADVANCE_INVOICE"), done, i == cur));
        }
        // "Yeni müştəri" hər hansı sonrakı addım tamamlananda da keçilmiş sayılır
        if (!out.isEmpty() && out.stream().skip(1).anyMatch(Dto.StepRes::done) && !out.get(0).done()) {
            Dto.StepRes n = out.get(0);
            out.set(0, new Dto.StepRes(n.key(), n.label(), n.optional(), true, n.current()));
        }
        return out;
    }

    /** Fiziki şəxs: NEW → PAID "Ödəniş və çek" → IN_PROGRESS "Quraşdırma" (istəyə bağlı) → DONE */
    private static List<Dto.StepRes> retailStepper(E.Deal d, Summary s) {
        List<String> keys = List.of("NEW", "PAID", "IN_PROGRESS", "DONE");
        int cur = keys.indexOf(d.stage);
        List<Dto.StepRes> out = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            boolean done = switch (k) {
                case "PAID" -> s.paid().compareTo(s.price()) >= 0;
                case "DONE" -> "DONE".equals(d.stage);
                default -> i < cur;
            };
            String label = switch (k) { case "NEW" -> "Yeni müştəri"; case "PAID" -> "Ödəniş və çek"; case "IN_PROGRESS" -> "Quraşdırma"; default -> "Tamamlandı"; };
            out.add(new Dto.StepRes(k, label, k.equals("IN_PROGRESS"), done, i == cur));
        }
        if (out.stream().skip(1).anyMatch(Dto.StepRes::done) && !out.get(0).done()) {
            Dto.StepRes n = out.get(0);
            out.set(0, new Dto.StepRes(n.key(), n.label(), n.optional(), true, n.current()));
        }
        return out;
    }

    private static boolean evidence(String k, E.Deal d, Set<String> docs) {
        return switch (k) {
            case "CONTRACT" -> d.contractNo != null || docs.contains("CONTRACT");
            case "PROTOCOL" -> d.protocolDate != null || docs.contains("PROTOCOL");
            case "ADVANCE_INVOICE" -> d.advanceInvoiceNo != null || d.advanceFileId != null;
            case "ACT" -> d.actNo != null || d.actFileId != null || docs.contains("ACT");
            case "INVOICE" -> d.invoiceNo != null || d.invoiceFileId != null;
            case "DONE" -> "DONE".equals(d.stage);
            default -> false;
        };
    }

    // ---- oxuma ----
    public Map<Long, List<E.PaymentAllocation>> byDeal() {
        return allocs.findAll().stream().collect(Collectors.groupingBy(a -> a.dealId));
    }

    public List<E.PaymentAllocation> forDealRaw(Long dealId) { return allocs.findByDealIdOrderByIdAsc(dealId); }

    private E.Deal deal(Long id) { return deals.findById(id).orElseThrow(() -> ApiException.notFound("Satış tapılmadı")); }
    private E.Movement movement(Long id) { return movements.findById(id).orElseThrow(() -> ApiException.notFound("Hərəkət tapılmadı")); }

    public BigDecimal allocatedOf(Long movementId) {
        return Money.r2(Money.sum(allocs.findByMovementIdOrderByIdAsc(movementId).stream().map(a -> a.amount).toList()));
    }

    public Dto.DealPayments forDeal(Long dealId) {
        E.Deal d = deal(dealId);
        List<E.PaymentAllocation> list = allocs.findByDealIdOrderByIdAsc(dealId);
        Summary s = summary(d, list);
        Map<Long, E.Account> accs = accounts.findAll().stream().collect(Collectors.toMap(a -> a.id, Function.identity()));
        List<Dto.PaymentItem> items = new ArrayList<>();
        for (E.PaymentAllocation a : list) {
            E.Movement m = movements.findById(a.movementId).orElse(null);
            if (m == null) continue;
            E.Account acc = accs.get(m.accountId);
            items.add(new Dto.PaymentItem(a.id, m.id, m.entryDate, acc == null ? null : acc.name, Money.s(m.amount), Money.s(a.amount), a.kind, a.fileId, a.note, m.note));
        }
        items.sort(Comparator.comparing(Dto.PaymentItem::date).thenComparing(Dto.PaymentItem::id));
        return new Dto.DealPayments(Money.s(s.price()), Money.s(s.paid()), Money.s(s.remaining()), s.status(), Money.s(s.advanceRequired()), Money.s(s.advancePaid()), items);
    }

    /** IN hərəkətləri (CUSTOMER_PAYMENT/OTHER), bölüşdürülməmiş qalığı olanlar */
    public List<Dto.UnallocatedRes> unallocated(String direction) {
        if (direction != null && !direction.isBlank() && !"IN".equals(direction)) throw ApiException.bad("Yalnız IN (mədaxil) hərəkətləri bağlana bilər");
        Map<Long, BigDecimal> sums = allocs.findAll().stream().collect(Collectors.groupingBy(a -> a.movementId, Collectors.reducing(BigDecimal.ZERO, a -> a.amount, BigDecimal::add)));
        Map<Long, E.Account> accs = accounts.findAll().stream().collect(Collectors.toMap(a -> a.id, Function.identity()));
        return movements.findAll().stream()
                .filter(m -> "IN".equals(m.direction) && ALLOCATABLE_PURPOSES.contains(m.purpose))
                .filter(m -> m.amount.subtract(sums.getOrDefault(m.id, BigDecimal.ZERO)).signum() > 0)
                .sorted(Comparator.comparing((E.Movement m) -> m.entryDate).thenComparing(m -> m.id).reversed())
                .map(m -> {
                    BigDecimal a = sums.getOrDefault(m.id, BigDecimal.ZERO);
                    E.Account acc = accs.get(m.accountId);
                    return new Dto.UnallocatedRes(m.id, m.entryDate, acc == null ? null : acc.name, Money.s(m.amount), Money.s(a), Money.s(m.amount.subtract(a)), m.note);
                }).toList();
    }

    /** Bank mətnini müqayisə üçün sadələşdirir: kiçik hərf, hərf və rəqəmdən başqa hər şey (boşluq, tire) silinir */
    static String normText(String s) {
        return s == null ? "" : s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static boolean has(String normNote, String value) {
        String v = normText(value);
        return v.length() >= 3 && normNote.contains(v);
    }

    /** Satışa bağlamaq üçün bölüşdürülməmiş mədaxillər, uyğunluq balı ilə sıralanmış (avtomatik bağlanmır) */
    public List<Dto.PaymentSuggestion> suggestions(Long dealId) {
        E.Deal d = deal(dealId);
        E.Customer c = customers.findById(d.customerId).orElse(null);
        Summary s = summary(d, allocs.findByDealIdOrderByIdAsc(dealId));
        BigDecimal advLeft = s.advanceRequired().subtract(s.advancePaid());
        List<BigDecimal> expected = new ArrayList<>();
        if (advLeft.signum() > 0) expected.add(Money.r2(advLeft));
        if (s.remaining().signum() > 0) expected.add(s.remaining());
        List<java.time.LocalDate> anchors = new ArrayList<>();
        if (d.advanceInvoiceDate != null) anchors.add(d.advanceInvoiceDate);
        if (d.contractDate != null) anchors.add(d.contractDate);
        if ("RETAIL".equals(d.paymentTerms)) anchors.add(d.saleDate);
        return rank(expected, anchors, c == null ? null : c.name, c == null ? null : c.voen, Arrays.asList(d.contractNo, d.advanceInvoiceNo, d.invoiceNo));
    }

    /** Hələ satışı olmayan sürətli satış üçün: məbləğ, tarix və alıcı adına görə təkliflər (eyni bal qaydaları) */
    public List<Dto.PaymentSuggestion> suggestionsFor(BigDecimal amount, java.time.LocalDate date, String name) {
        List<BigDecimal> expected = amount == null || amount.signum() <= 0 ? List.of() : List.of(Money.r2(amount));
        return rank(expected, date == null ? List.of() : List.of(date), name, null, List.of());
    }

    private List<Dto.PaymentSuggestion> rank(List<BigDecimal> expected, List<java.time.LocalDate> anchors, String name, String voen, List<String> numbers) {
        List<Dto.PaymentSuggestion> out = new ArrayList<>();
        for (Dto.UnallocatedRes m : unallocated("IN")) {
            String note = normText(m.note());
            int score = 0;
            List<String> why = new ArrayList<>();
            if (has(note, numbers.size() > 0 ? numbers.get(0) : null)) { score += 50; why.add("müqavilə nömrəsi"); }
            else if (numbers.stream().skip(1).anyMatch(n -> has(note, n))) { score += 50; why.add("qaimə nömrəsi"); }
            BigDecimal rem = new BigDecimal(m.remaining());
            if (expected.stream().anyMatch(e -> e.compareTo(rem) == 0)) { score += 30; why.add("məbləğ eynidir"); }
            if (has(note, name)) { score += 20; why.add("kontragent adı"); }
            else if (has(note, voen)) { score += 20; why.add("VÖEN"); }
            if (anchors.stream().anyMatch(a -> Math.abs(java.time.temporal.ChronoUnit.DAYS.between(a, m.date())) <= 14)) { score += 10; why.add("tarix yaxındır"); }
            out.add(new Dto.PaymentSuggestion(m.id(), m.date(), m.accountName(), m.amount(), m.remaining(), m.note(), score, why));
        }
        out.sort(Comparator.comparingInt(Dto.PaymentSuggestion::score).reversed().thenComparing(Dto.PaymentSuggestion::date, Comparator.reverseOrder()));
        return out;
    }

    public List<Dto.MovementAllocRes> movementAllocations(Long movementId) {
        movement(movementId);
        return allocs.findByMovementIdOrderByIdAsc(movementId).stream().map(this::toMovementAlloc).toList();
    }

    Dto.MovementAllocRes toMovementAlloc(E.PaymentAllocation a) {
        E.Deal d = deals.findById(a.dealId).orElse(null);
        E.Customer c = d == null ? null : customers.findById(d.customerId).orElse(null);
        return new Dto.MovementAllocRes(a.id, a.movementId, a.dealId, d == null ? null : d.contractNo, c == null ? null : c.name, Money.s(a.amount), a.kind, a.fileId, a.note);
    }

    // ---- yazma ----
    private String defaultKind(E.Deal d, List<E.PaymentAllocation> dealAllocs) {
        Summary s = summary(d, dealAllocs);
        return s.advanceRequired().signum() > 0 && !advanceDone(s) ? "ADVANCE" : "FINAL";
    }

    private record PaymentValues(BigDecimal amount, String kind, Long fileId, String note) {}

    /** Əsas qaydalar: yalnız IN; hərəkətin bölüşdürülmüş cəmi ≤ məbləği. Dəyişdirilən bağlantının özü hesaba qatılmır. */
    private E.PaymentAllocation write(E.PaymentAllocation a, E.Deal d, E.Movement m, PaymentValues v) {
        if (!"IN".equals(m.direction)) throw ApiException.bad("Yalnız mədaxil (IN) hərəkəti satışa bağlana bilər");
        if (a.id == null && !ALLOCATABLE_PURPOSES.contains(m.purpose))
            throw ApiException.bad("Satışa yalnız təyinatı \"Müştəri ödənişi\" olan mədaxil bağlana bilər. Əvvəlcə hərəkətin təyinatını dəyişin");
        List<E.PaymentAllocation> dealAllocs = allocs.findByDealIdOrderByIdAsc(d.id).stream().filter(x -> !Objects.equals(x.id, a.id)).toList();
        BigDecimal movUsed = Money.sum(allocs.findByMovementIdOrderByIdAsc(m.id).stream().filter(x -> !Objects.equals(x.id, a.id)).map(x -> x.amount).toList());
        BigDecimal movRemaining = m.amount.subtract(movUsed);
        BigDecimal dealRemaining = Money.r2(d.price).subtract(Money.sum(dealAllocs.stream().map(x -> x.amount).toList())).max(BigDecimal.ZERO);
        BigDecimal amount = v.amount != null ? Money.r2(v.amount) : movRemaining.min(dealRemaining);
        if (amount.signum() <= 0) throw ApiException.bad(v.amount == null
                ? "Bağlanacaq məbləğ yoxdur: hərəkətin boş qalığı bitib və ya satış tam ödənilib, məbləği əl ilə yazın"
                : "Məbləğ sıfırdan böyük olmalıdır");
        if (amount.compareTo(movRemaining) > 0)
            throw ApiException.conflict("artiq_bolusdurme", "Bağlanan məbləğ hərəkətin boş qalığından (" + Money.s(movRemaining) + " ₼) çoxdur");
        String kind = v.kind == null || v.kind.isBlank() ? defaultKind(d, dealAllocs) : v.kind;
        if (!KINDS.contains(kind)) throw ApiException.bad("Növ ADVANCE, FINAL və ya OTHER olmalıdır");
        if (v.fileId != null && !files.existsById(v.fileId)) throw ApiException.bad("Fayl tapılmadı");
        a.movementId = m.id;
        a.dealId = d.id;
        a.amount = amount;
        a.kind = kind;
        a.fileId = v.fileId;
        a.note = blank(v.note);
        if (a.createdAt == null) a.createdAt = LocalDateTime.now();
        return allocs.save(a);
    }

    @Transactional
    public Dto.DealPayments create(Long dealId, Dto.PaymentReq r) {
        if (r.movementId() == null) throw ApiException.bad("Bank hərəkəti seçilməlidir");
        write(new E.PaymentAllocation(), deal(dealId), movement(r.movementId()), new PaymentValues(r.amount(), r.kind(), r.fileId(), r.note()));
        return forDeal(dealId);
    }

    /** Hərəkət yaradılarkən eyni anda bağlamaq (MovementReq.allocation) */
    @Transactional
    public void allocateNew(E.Movement m, Dto.PaymentReq r) {
        if (r.dealId() == null) throw ApiException.bad("Bağlanacaq satış seçilməlidir");
        write(new E.PaymentAllocation(), deal(r.dealId()), m, new PaymentValues(r.amount(), r.kind(), r.fileId(), r.note()));
    }

    /** Dəyişdirmə: amount/kind boşdursa köhnəsi qalır; fileId və note tam əvəzlənir; dealId verilsə başqa satışa keçirilir */
    @Transactional
    public Dto.DealPayments update(Long id, Dto.PaymentReq r) {
        E.PaymentAllocation a = allocs.findById(id).orElseThrow(() -> ApiException.notFound("Ödəniş bağlantısı tapılmadı"));
        E.Deal d = deal(r.dealId() != null ? r.dealId() : a.dealId);
        write(a, d, movement(a.movementId), new PaymentValues(r.amount() != null ? r.amount() : a.amount, r.kind() != null ? r.kind() : a.kind, r.fileId(), r.note()));
        return forDeal(d.id);
    }

    @Transactional
    public void delete(Long id) {
        allocs.delete(allocs.findById(id).orElseThrow(() -> ApiException.notFound("Ödəniş bağlantısı tapılmadı")));
    }

    /** Satış silinəndə bağlantılar götürülür (hərəkətlər qalır) */
    @Transactional
    public void deleteForDeal(Long dealId) { allocs.deleteByDealId(dealId); }

    /** Hərəkətin məbləği/istiqaməti dəyişəndə: bağlı məbləğdən az ola bilməz, bağlı hərəkət OUT ola bilməz */
    public void checkMovementChange(E.Movement m, BigDecimal newAmount, String newDirection, String newPurpose) {
        BigDecimal used = allocatedOf(m.id);
        if (used.signum() == 0) return;
        if (!ALLOCATABLE_PURPOSES.contains(newPurpose)) throw ApiException.conflict("hereket_bagli", "Hərəkət satışlara bağlıdır, təyinatı \"Müştəri ödənişi\" olmalıdır. Əvvəlcə bağlantıları ayırın");
        if (!"IN".equals(newDirection)) throw ApiException.conflict("hereket_bagli", "Hərəkət satışlara bağlıdır, əvvəlcə bağlantıları ayırın");
        if (Money.r2(newAmount).compareTo(used) < 0)
            throw ApiException.conflict("hereket_bagli", "Məbləğ satışlara bağlanmış cəmdən (" + Money.s(used) + " ₼) az ola bilməz");
    }

    public void checkMovementDelete(Long movementId) {
        if (allocs.existsByMovementId(movementId))
            throw ApiException.conflict("hereket_bagli", "Hərəkət satışlara bağlıdır, silməzdən əvvəl bağlantıları ayırın");
    }
}
