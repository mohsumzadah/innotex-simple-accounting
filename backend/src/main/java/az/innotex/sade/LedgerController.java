package az.innotex.sade;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class LedgerController {
    private final LedgerService svc;
    private final FxService fx;

    private final PaymentService payments;

    public LedgerController(LedgerService svc, FxService fx, PaymentService payments) { this.svc = svc; this.fx = fx; this.payments = payments; }

    @GetMapping("/accounts") public List<Dto.AccountRes> accounts() { return svc.listAccounts(); }
    @PostMapping("/accounts") @ResponseStatus(HttpStatus.CREATED) public Dto.AccountRes createAccount(@RequestBody Dto.AccountReq r) { return svc.createAccount(r); }
    @PutMapping("/accounts/{id}") public Dto.AccountRes updateAccount(@PathVariable Long id, @RequestBody Dto.AccountReq r) { return svc.updateAccount(id, r); }
    @DeleteMapping("/accounts/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteAccount(@PathVariable Long id) { svc.deleteAccount(id); }
    @PostMapping("/accounts/{id}/default") public Dto.AccountRes setDefaultAccount(@PathVariable Long id) { return svc.setDefaultAccount(id); }

    @GetMapping("/movements")
    public List<Dto.MovementRes> movements(@RequestParam(required = false) Long accountId,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return svc.listMovements(accountId, from, to);
    }
    @PostMapping("/movements") @ResponseStatus(HttpStatus.CREATED) public Dto.MovementRes createMovement(@RequestBody Dto.MovementReq r) { return svc.createMovement(r); }
    @PutMapping("/movements/{id}") public Dto.MovementRes updateMovement(@PathVariable Long id, @RequestBody Dto.MovementReq r) { return svc.updateMovement(id, r); }
    @DeleteMapping("/movements/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteMovement(@PathVariable Long id) { svc.deleteMovement(id); }

    @GetMapping("/movements/unallocated") public List<Dto.UnallocatedRes> unallocated(@RequestParam(required = false) String direction) { return payments.unallocated(direction); }
    @GetMapping("/movements/{id}/allocations") public List<Dto.MovementAllocRes> movementAllocations(@PathVariable Long id) { return payments.movementAllocations(id); }

    @GetMapping("/expenses")
    public List<Dto.ExpenseRes> expenses(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return svc.listExpenses(from, to);
    }
    @PostMapping("/expenses") @ResponseStatus(HttpStatus.CREATED) public Dto.ExpenseRes createExpense(@RequestBody Dto.ExpenseReq r) { return svc.createExpense(r); }
    @PutMapping("/expenses/{id}") public Dto.ExpenseRes updateExpense(@PathVariable Long id, @RequestBody Dto.ExpenseReq r) { return svc.updateExpense(id, r); }
    @DeleteMapping("/expenses/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteExpense(@PathVariable Long id) { svc.deleteExpense(id); }

    @GetMapping("/expense-items") public List<Dto.ExpenseItemRes> items() { return svc.listItems(); }
    @GetMapping("/expense-items/missing") public List<Dto.MissingItem> missing(@RequestParam String month) { return svc.missingItems(month); }
    @PostMapping("/expense-items") @ResponseStatus(HttpStatus.CREATED) public Dto.ExpenseItemRes createItem(@RequestBody Dto.ExpenseItemReq r) { return svc.createItem(r); }
    @PutMapping("/expense-items/{id}") public Dto.ExpenseItemRes updateItem(@PathVariable Long id, @RequestBody Dto.ExpenseItemReq r) { return svc.updateItem(id, r); }
    @DeleteMapping("/expense-items/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteItem(@PathVariable Long id) { svc.deleteItem(id); }
}
