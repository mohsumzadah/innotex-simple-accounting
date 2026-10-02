package az.innotex.sade;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** JPA obyektləri (sadəlik üçün açıq sahələr, əlaqələr yalnız id ilə). Sütun adları snake_case-ə çevrilir. */
public final class E {
    private E() {}

    @Entity @Table(name = "app_user")
    public static class AppUser {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public String email;
        public String passwordHash;
        public String name = "";
        public String role = "ADMIN";
        public boolean active = true;
        public LocalDateTime createdAt;
    }

    @Entity @Table(name = "session_token")
    public static class SessionToken {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public String tokenHash;
        public Long userId;
        public LocalDateTime createdAt;
    }

    @Entity @Table(name = "settings")
    public static class Settings {
        @Id public Long id;
        public String companyName, voen, address, director, bank, iban, bankCode, swift, phone, email;
        public String bankVoen, correspondentAccount;
        public BigDecimal profitTaxRate;
        public String taxMethod;
    }

    @Entity @Table(name = "payroll_rate")
    public static class PayrollRate {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public String validFrom;
        public BigDecimal dsmfLimit, dsmfEmpLow, dsmfEmpHigh, dsmfErLow, dsmfErHigh, unempEmp, unempEr,
                medLimit, medLow, medHigh, incomeLimit, incomeExempt, incomeLow, incomeHigh;
    }

    @Entity @Table(name = "work_calendar")
    public static class WorkCalendar {
        @Id public String period;
        public int days;
    }

    @Entity @Table(name = "template")
    public static class Template {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public String code, title;
        @Column(length = 1000000) public String html;
        public String format = "HTML";
        public Long fileId;
        public boolean isDefault;
    }
    @Entity @Table(name = "deal_document")
    public static class DealDocument {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public Long dealId, templateId;
        public String code, title;
        @Column(length = 1000000) public String html;
        public String format = "HTML";
        public Long fileId, previousFileId;
        public LocalDateTime createdAt, updatedAt;
    }


    @Entity @Table(name = "stored_file")
    public static class StoredFile {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public String name;
        public long sizeBytes;
        public String storedName;
        public String contentType, sha256;
        public LocalDateTime createdAt;
    }

    @Entity @Table(name = "account")
    public static class Account {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public String name, type, currency;
        public String bankName, iban, bankCode, bankVoen, swift, correspondentAccount, cardNumber, note;
        public BigDecimal openingBalance;
        public LocalDate openingDate;
        public boolean isDefault;
    }

    @Entity @Table(name = "account_statement")
    public static class AccountStatement {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public Long accountId, fileId;
        public LocalDate periodFrom, periodTo;
        public String note;
        public LocalDateTime createdAt;
    }

    @Entity @Table(name = "expense")
    public static class Expense {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public LocalDate entryDate, docDate;
        public String vendor, category, description, currency, note;
        public BigDecimal amount, rate, amountAzn;
        public Long accountId, fileId, itemId;
        public boolean deductible;
    }

    @Entity @Table(name = "fx_rate") @IdClass(FxRate.Key.class)
    public static class FxRate {
        public static class Key implements java.io.Serializable {
            public LocalDate rateDate;
            public String currency;
            public Key() {}
            public Key(LocalDate rateDate, String currency) { this.rateDate = rateDate; this.currency = currency; }
            @Override public boolean equals(Object o) { return o instanceof Key k && java.util.Objects.equals(rateDate, k.rateDate) && java.util.Objects.equals(currency, k.currency); }
            @Override public int hashCode() { return java.util.Objects.hash(rateDate, currency); }
        }
        @Id public LocalDate rateDate;
        @Id public String currency;
        public BigDecimal rate;
        public String source;
        public LocalDateTime fetchedAt;
    }

    @Entity @Table(name = "expense_item")
    public static class ExpenseItem {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public String name, vendor, category, currency, recurrence, note;
        public BigDecimal defaultAmount;
        public Long accountId;
        public boolean deductible, active;
    }

    @Entity @Table(name = "movement")
    public static class Movement {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public LocalDate entryDate;
        public Long accountId;
        public String direction, purpose, note;
        public BigDecimal amount;
        public Long fileId, expenseId;
        public Long statementId;
        /** @deprecated məntiqdə istifadə olunmur; bağlantılar {@link PaymentAllocation} ilədir (V12) */
        public Long dealId;
    }

    /** Bank mədaxilinin satışa (qismən) bağlanması: bir hərəkət bir neçə satışa, bir satış bir neçə hərəkətə bağlana bilər */
    @Entity @Table(name = "payment_allocation")
    public static class PaymentAllocation {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public Long movementId, dealId, fileId;
        public BigDecimal amount;
        public String kind, note;
        public LocalDateTime createdAt;
    }

    @Entity @Table(name = "employee")
    public static class Employee {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public String name, jobTitle, workplace;
        public String bankName, iban, cardNumber, fin, note;
        public java.time.LocalDate cardExpiry;
        public BigDecimal gross;
        public boolean active;
    }

    @Entity @Table(name = "payroll_run")
    public static class PayrollRun {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public String period, status;
        public int normDays;
    }

    @Entity @Table(name = "payroll_line")
    public static class PayrollLine {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public Long runId, employeeId;
        public String name, jobTitle, workplace;
        public BigDecimal gross, accrued, income, dsmfEmp, medEmp, unempEmp, net, dsmfEr, medEr, unempEr, employerCost;
        public int normDays, workedDays;
    }

    @Entity @Table(name = "customer")
    public static class Customer {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public String name, voen, address, director, bank, iban, bankCode, swift, phone, email;
        public String kind, bankVoen, correspondentAccount, note, fin, entityType, cardNumber;
    }

    @Entity @Table(name = "product")
    public static class Product {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public String name, code, unit, description;
        public BigDecimal defaultPrice;
        public Integer defaultComputers;
        public String defaultPaymentTerms;
        public BigDecimal defaultAdvancePercent;
        public boolean active;
    }

    @Entity @Table(name = "deal")
    public static class Deal {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public Long customerId, productId;
        public String product, description, stage, note;
        public BigDecimal price;
        public int computers;
        public int quantity = 1;
        public String contractNo;
        public LocalDate contractDate, protocolDate;
        public String advanceInvoiceNo;
        public LocalDate advanceInvoiceDate;
        public BigDecimal advanceAmount;
        public Long advanceFileId;
        /** @deprecated köhnə ödəniş sahələri: məntiqdə istifadə olunmur, ödənişlər {@link PaymentAllocation} ilədir (V12) */
        public LocalDate paymentDate;
        public BigDecimal paymentAmount;
        public Long paymentFileId, paymentMovementId;
        public String paymentTerms = "POSTPAID";
        public BigDecimal advancePercent = BigDecimal.ZERO;
        public String actNo;
        public LocalDate actDate;
        public Long actFileId;
        public String invoiceNo;
        public LocalDate invoiceDate;
        public BigDecimal invoiceAmount;
        public Long invoiceFileId;
        public LocalDate saleDate;
        public LocalDateTime createdAt;
        public boolean cancelled;
        public String cancelReason;
        public LocalDateTime cancelledAt;
    }

    @Entity @Table(name = "deal_event")
    public static class DealEvent {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public Long dealId;
        public LocalDate eventDate;
        public LocalDateTime createdAt;
        public String fromStage, toStage, note;
    }

    @Entity @Table(name = "payment_code")
    public static class PaymentCode {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public int sortOrder;
        public String codeKey, title, budgetCode;
        public boolean active;
    }

    /** Maaş cədvəli sətirinin (vergi/NET) bank hərəkətinə bağlantısı */
    @Entity @Table(name = "payroll_payment")
    public static class PayrollPayment {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public Long runId, employeeId, movementId, fileId;
        public String codeKey, note;
        public BigDecimal amount;
        public LocalDate paidDate;
    }

    @Entity @Table(name = "payroll_run_file")
    public static class PayrollRunFile {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        public Long runId, fileId;
        public String title;
        public LocalDateTime createdAt;
    }
}
