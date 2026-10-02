package az.innotex.sade;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Repozitoriyalar: siyasət yoxdur, yalnız sorğular */
public final class R {
    private R() {}

    public interface Users extends JpaRepository<E.AppUser, Long> {
        Optional<E.AppUser> findByEmailIgnoreCase(String email);
        List<E.AppUser> findAllByOrderByIdAsc();
        long countByRoleAndActiveTrue(String role);
    }
    public interface Sessions extends JpaRepository<E.SessionToken, Long> {
        Optional<E.SessionToken> findByTokenHash(String tokenHash);
        void deleteByUserId(Long userId);
    }
    public interface SettingsRepo extends JpaRepository<E.Settings, Long> {}
    public interface Rates extends JpaRepository<E.PayrollRate, Long> {
        List<E.PayrollRate> findAllByOrderByValidFromAscIdAsc();
    }
    public interface Calendar extends JpaRepository<E.WorkCalendar, String> {
        List<E.WorkCalendar> findByPeriodStartingWith(String prefix);
    }
    public interface Templates extends JpaRepository<E.Template, Long> {
        List<E.Template> findByCodeOrderByIdAsc(String code);
        List<E.Template> findAllByOrderByCodeAscIdAsc();
    }
    public interface DealDocs extends JpaRepository<E.DealDocument, Long> {
        List<E.DealDocument> findByDealIdOrderByIdAsc(Long dealId);
        void deleteByDealId(Long dealId);
    }
    public interface Files extends JpaRepository<E.StoredFile, Long> {}
    public interface Statements extends JpaRepository<E.AccountStatement, Long> {
        List<E.AccountStatement> findByAccountIdOrderByPeriodFromDescIdDesc(Long accountId);
    }
    public interface Accounts extends JpaRepository<E.Account, Long> {}
    public interface Expenses extends JpaRepository<E.Expense, Long> {
        boolean existsByAccountId(Long accountId);
        boolean existsByItemId(Long itemId);
    }
    public interface ExpenseItems extends JpaRepository<E.ExpenseItem, Long> {
        Optional<E.ExpenseItem> findByNameIgnoreCase(String name);
    }
    public interface FxRates extends JpaRepository<E.FxRate, E.FxRate.Key> {}
    public interface Movements extends JpaRepository<E.Movement, Long> {
        boolean existsByStatementId(Long statementId);
        long countByStatementId(Long statementId);
        boolean existsByAccountId(Long accountId);
        Optional<E.Movement> findFirstByExpenseId(Long expenseId);
    }
    public interface Employees extends JpaRepository<E.Employee, Long> {}
    public interface Runs extends JpaRepository<E.PayrollRun, Long> {
        boolean existsByPeriod(String period);
    }
    public interface Lines extends JpaRepository<E.PayrollLine, Long> {
        List<E.PayrollLine> findByRunIdOrderById(Long runId);
        void deleteByRunId(Long runId);
    }
    public interface PaymentCodes extends JpaRepository<E.PaymentCode, Long> {
        List<E.PaymentCode> findAllByOrderBySortOrderAscIdAsc();
    }
    public interface Customers extends JpaRepository<E.Customer, Long> {}
    public interface Products extends JpaRepository<E.Product, Long> {
        Optional<E.Product> findByNameIgnoreCase(String name);
    }
    public interface Deals extends JpaRepository<E.Deal, Long> {
        boolean existsByProductId(Long productId);
        boolean existsByCustomerId(Long customerId);
    }
    public interface Allocations extends JpaRepository<E.PaymentAllocation, Long> {
        List<E.PaymentAllocation> findByDealIdOrderByIdAsc(Long dealId);
        List<E.PaymentAllocation> findByMovementIdOrderByIdAsc(Long movementId);
        boolean existsByMovementId(Long movementId);
        void deleteByDealId(Long dealId);
    }
    public interface Events extends JpaRepository<E.DealEvent, Long> {
        List<E.DealEvent> findByDealIdOrderByIdDesc(Long dealId);
        void deleteByDealId(Long dealId);
    }
    public interface PayrollPayments extends JpaRepository<E.PayrollPayment, Long> {
        List<E.PayrollPayment> findByRunIdOrderById(Long runId);
        List<E.PayrollPayment> findByMovementId(Long movementId);
        boolean existsByMovementId(Long movementId);
        void deleteByRunId(Long runId);
    }
    public interface PayrollRunFiles extends JpaRepository<E.PayrollRunFile, Long> {
        List<E.PayrollRunFile> findByRunIdOrderById(Long runId);
        void deleteByRunId(Long runId);
    }
}
