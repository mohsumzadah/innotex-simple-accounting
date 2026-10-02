package az.innotex.sade;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** API müqaviləsi (SPEC.md). Sorğularda pul BigDecimal, cavablarda string (2 rəqəm). */
public final class Dto {
    private Dto() {}

    // ---- giriş ----
    public record LoginReq(String email, String password) {}
    public record TokenRes(String token) {}
    public record MeRes(Long id, String email, String name, String role) {}
    public record UserRes(Long id, String email, String name, String role, boolean active, java.time.LocalDateTime createdAt) {}
    /** Yaradanda email və password məcburidir; dəyişəndə password boşdursa parol dəyişmir */
    public record UserReq(String email, String name, String role, Boolean active, String password) {}
    public record PasswordReq(String current, @JsonProperty("new") String newPassword) {}

    // ---- ayarlar ----
    public record SettingsReq(String companyName, String voen, String address, String director, String bank, String iban,
                              String bankCode, String bankVoen, String swift, String correspondentAccount, String phone, String email, BigDecimal profitTaxRate) {}
    public record SettingsRes(String companyName, String voen, String address, String director, String bank, String iban,
                              String bankCode, String bankVoen, String swift, String correspondentAccount, String phone, String email, String profitTaxRate, String taxMethod) {}

    public record RateReq(String validFrom, BigDecimal dsmfLimit, BigDecimal dsmfEmpLow, BigDecimal dsmfEmpHigh,
                          BigDecimal dsmfErLow, BigDecimal dsmfErHigh, BigDecimal unempEmp, BigDecimal unempEr,
                          BigDecimal medLimit, BigDecimal medLow, BigDecimal medHigh, BigDecimal incomeLimit,
                          BigDecimal incomeExempt, BigDecimal incomeLow, BigDecimal incomeHigh) {}
    public record RateRes(Long id, String validFrom, String dsmfLimit, String dsmfEmpLow, String dsmfEmpHigh,
                          String dsmfErLow, String dsmfErHigh, String unempEmp, String unempEr,
                          String medLimit, String medLow, String medHigh, String incomeLimit,
                          String incomeExempt, String incomeLow, String incomeHigh) {}

    public record CalendarItem(String month, int days) {}
    public record TemplateReq(String code, String title, String format, Long fileId, String html, @JsonProperty("isDefault") Boolean isDefault) {}
    public record TemplateRes(Long id, String code, String title, String format, Long fileId, String html, @JsonProperty("isDefault") boolean isDefault) {}
    public record DealDocCreateReq(String code, Long templateId) {}
    public record DealDocReq(String title, String html) {}
    public record DealDocRes(Long id, Long dealId, String code, Long templateId, String title, String html, String format, Long fileId, boolean hasPrevious, String createdAt, String updatedAt) {}

    // ---- hesablar, hərəkətlər, xərclər ----
    public record AccountReq(String name, String type, String currency, BigDecimal openingBalance, LocalDate openingDate,
                            String bankName, String iban, String bankCode, String bankVoen, String swift, String correspondentAccount, String cardNumber, String note) {}
    public record AccountRes(Long id, String name, String type, String currency, String openingBalance, LocalDate openingDate, String balance,
                            String bankName, String iban, String bankCode, String bankVoen, String swift, String correspondentAccount, String cardNumber, String note, @JsonProperty("isDefault") boolean isDefault) {}

    public record MovementReq(LocalDate date, Long accountId, String direction, BigDecimal amount, String purpose, String note, Long fileId, Long dealId, PaymentReq allocation, Long statementId) {}
    public record MovementRes(Long id, LocalDate date, Long accountId, String accountName, String direction, String amount,
                              String purpose, String note, Long fileId, Long dealId, Long expenseId, String allocated, List<MovementAllocRes> allocations, Long statementId) {}

    public record ExpenseReq(LocalDate date, String vendor, String category, String description, BigDecimal amount, String currency,
                             BigDecimal rate, BigDecimal amountAzn, Long accountId, Boolean deductible, Long fileId, String note, Long itemId, LocalDate docDate) {}
    public record StatementRes(Long id, Long accountId, LocalDate periodFrom, LocalDate periodTo, Long fileId, String fileName, long fileSize, String note, String createdAt, long movementCount) {}
    public record ExpenseRes(Long id, LocalDate date, String vendor, String category, String description, String amount, String currency,
                             String rate, String amountAzn, Long accountId, boolean deductible, Long fileId, String note, Long itemId, LocalDate docDate, Long statementId) {}

    public record ExpenseItemReq(String name, String vendor, String category, String currency, BigDecimal defaultAmount, Long accountId,
                                 Boolean deductible, String recurrence, Boolean active, String note) {}
    public record ExpenseItemRes(Long id, String name, String vendor, String category, String currency, String defaultAmount, Long accountId,
                                 String accountName, boolean deductible, String recurrence, boolean active, String note) {}
    public record FxRes(LocalDate date, String currency, String rate, String source, boolean cached) {}
    public record MissingItem(Long itemId, String name, String vendor, String defaultAmount, String currency, String lastAmountAzn, LocalDate lastDate) {}

    // ---- maaş ----
    public record EmployeeReq(String name, String position, String workplace, BigDecimal gross, Boolean active,
                              String bankName, String iban, String cardNumber, String fin, String note, String cardExpiry) {}
    public record EmployeeRes(Long id, String name, String position, String workplace, String gross, boolean active,
                              String bankName, String iban, String cardNumber, String fin, String note, String cardExpiry) {}

    public record RunCreateReq(String month) {}
    public record LineReq(Integer workedDays) {}
    public record RunSummary(Long id, String month, String status, String totalGross, String totalNet, String totalEmployerCost) {}
    public record LineRes(Long id, Long employeeId, String name, String position, String workplace, String gross, int normDays,
                          int workedDays, String accrued, String income, String dsmfEmp, String medEmp, String unempEmp, String net,
                          String dsmfEr, String medEr, String unempEr, String employerCost) {}
    public record PaymentCodeReq(String title, String budgetCode, Boolean active, Integer sortOrder) {}
    public record PaymentCodeRes(Long id, int sortOrder, String key, String title, String budgetCode, boolean active) {}
    public record PayLink(Long id, Long movementId, LocalDate date, String amount, String note, Long fileId) {}
    public record PaymentRes(int order, String key, String title, String budgetCode, String amount, String purpose,
                             String status, String paidAmount, Long fileId, List<PayLink> links) {}
    public record EmployeePaymentRes(Long employeeId, String name, String bankName, String iban, String cardNumber, String cardExpiry, String amount, String purpose,
                                     String status, String paidAmount, Long fileId, List<PayLink> links) {}
    public record PayLinkReq(String codeKey, Long employeeId, Long movementId, BigDecimal amount, Long fileId, String note) {}
    public record MovementSuggestion(Long movementId, LocalDate date, String accountName, String amount, String remaining, String purpose, String note, int score, List<String> reasons) {}
    public record RunFileRes(Long id, Long fileId, String title, String name, long size, String createdAt) {}
    public record UnpaidLine(String key, String title, String amount, String paidAmount, String status) {}
    public record UnpaidRun(Long runId, String month, List<UnpaidLine> lines) {}
    public record CancelReq(String reason) {}
    public record PaymentsRes(List<PaymentRes> taxes, List<EmployeePaymentRes> employees) {}
    public record RunRes(Long id, String month, String status, int normDays, List<LineRes> lines, LineRes totals) {}

    // ---- müştərilər, satışlar ----
    public record CustomerDto(Long id, String name, String voen, String kind, String address, String director, String bank, String iban,
                              String bankCode, String bankVoen, String swift, String correspondentAccount, String phone, String email, String note, String fin, String entityType, String cardNumber) {}

    public record DealReq(Long customerId, Long productId, String product, String description, BigDecimal price, Integer computers, String note, LocalDate saleDate,
                          String paymentTerms, BigDecimal advancePercent, Boolean startContract, Integer quantity) {}
    public record StageReq(String stage, String contractNo, LocalDate contractDate, LocalDate protocolDate,
                           String advanceInvoiceNo, LocalDate advanceInvoiceDate, BigDecimal advanceAmount, Long advanceFileId,
                           LocalDate paymentDate, BigDecimal paymentAmount, Long paymentFileId, Long accountId,
                           String actNo, LocalDate actDate, Long actFileId,
                           String invoiceNo, LocalDate invoiceDate, BigDecimal invoiceAmount, Long invoiceFileId, String note, LocalDate eventDate) {}
    public record DealRes(Long id, Long customerId, String customerName, Long productId, String productCode, String product, String description, String price, int computers,
                          String stage, String contractNo, LocalDate contractDate, LocalDate protocolDate,
                          String advanceInvoiceNo, LocalDate advanceInvoiceDate, String advanceAmount, Long advanceFileId,
                          LocalDate paymentDate, String paymentAmount, Long paymentFileId, Long paymentMovementId,
                          String actNo, LocalDate actDate, Long actFileId,
                          String invoiceNo, LocalDate invoiceDate, String invoiceAmount, Long invoiceFileId,
                          String note, String createdAt, LocalDate saleDate,
                          String paymentTerms, String advancePercent, String advanceRequired, String advancePaid, String paid, String remaining,
                          String paymentStatus, boolean awaitingPayment, List<StepRes> stepper, LocalDate stageSince,
                          boolean cancelled, String cancelReason, String cancelledAt, boolean hasAllocations, int quantity) {}
    public record StepRes(String key, String label, boolean optional, boolean done, boolean current) {}

    // ---- ödəniş bağlantıları ----
    /** POST/PUT /deals/{id}/payments; movementId yalnız POST-da, dealId (başqa satışa keçirmək) yalnız PUT-da; MovementReq.allocation-da dealId məcburidir */
    public record PaymentReq(Long movementId, BigDecimal amount, String kind, Long fileId, String note, Long dealId) {}
    public record PaymentItem(Long id, Long movementId, LocalDate date, String accountName, String movementAmount, String amount, String kind,
                              Long fileId, String note, String movementNote) {}
    public record DealPayments(String price, String paid, String remaining, String status, String advanceRequired, String advancePaid, List<PaymentItem> items) {}
    public record UnallocatedRes(Long id, LocalDate date, String accountName, String amount, String allocated, String remaining, String note) {}
    /** POST /deals/quick-retail (multipart: "request" JSON hissəsi + ixtiyari "file") */
    public record QuickBuyer(String name, String phone, String email, String fin) {}
    public record QuickMovement(Long accountId, LocalDate date, BigDecimal amount, String note) {}
    public record QuickRetailReq(Long customerId, QuickBuyer buyer, Long productId, String product, BigDecimal price, LocalDate saleDate,
                                 Long movementId, QuickMovement movement, Boolean installed, String note) {}
    public record PaymentSuggestion(Long movementId, LocalDate date, String accountName, String amount, String remaining, String note, int score, List<String> reasons) {}
    public record MovementAllocRes(Long id, Long movementId, Long dealId, String contractNo, String customerName, String amount, String kind, Long fileId, String note) {}
    public record AwaitingPayment(Long dealId, String customerName, String contractNo, LocalDate invoiceDate, String price, String paid, String remaining) {}
    public record ProductReq(String name, String code, String unit, BigDecimal defaultPrice, Integer defaultComputers, String description, Boolean active,
                             String defaultPaymentTerms, BigDecimal defaultAdvancePercent) {}
    public record ProductRes(Long id, String name, String code, String unit, String defaultPrice, Integer defaultComputers, String description, boolean active,
                             String defaultPaymentTerms, String defaultAdvancePercent) {}
    public record ProductAmount(String product, String amount) {}
    public record EventRes(Long id, LocalDate date, String fromStage, String toStage, String note, String createdAt) {}

    public record FileRes(Long id, String name, long size) {}

    // ---- hesabatlar ----
    public record DashQuarter(int year, int q, String income, String expenses, String profit, String profitTax) {}
    public record DashAccount(Long id, String name, String balance) {}
    public record ExpiringCard(Long employeeId, String name, String cardExpiry, long daysLeft) {}
    public record Dashboard(DashQuarter quarter, List<DashAccount> accounts, String ownerDebt, Map<String, Integer> dealsByStage, List<ExpiringCard> expiringCards,
                           List<AwaitingPayment> awaitingPayments, String method, List<String> warnings, List<UnpaidRun> unpaidPayroll) {}

    public record Period(LocalDate from, LocalDate to) {}
    /** kind: INVOICE (qaimə ilə) | RETAIL (fiziki şəxs, tamamlanma tarixi ilə; invoiceNo null) */
    public record IncomeItem(LocalDate date, String invoiceNo, String customer, String amount, String kind) {}
    public record CategoryAmount(String category, String amount) {}
    public record PayrollMonth(String month, String gross, String income, String dsmfEmp, String dsmfEr, String medEmp, String medEr,
                               String unempEmp, String unempEr, String net) {}
    public record PayrollBlock(List<PayrollMonth> months, PayrollMonth total) {}
    public record QuarterSummary(int q, String income, String expenses, String profit, String profitTax) {}
    public record Report(Period period, String income, List<IncomeItem> incomeItems, String expenses, List<CategoryAmount> expenseByCategory, List<ProductAmount> incomeByProduct,
                         String bankFees, String payrollCost, String profit, String profitTaxRate, String profitTax, PayrollBlock payroll,
                         @JsonInclude(JsonInclude.Include.NON_NULL) List<QuarterSummary> quarters, String method, List<String> warnings) {}
}
