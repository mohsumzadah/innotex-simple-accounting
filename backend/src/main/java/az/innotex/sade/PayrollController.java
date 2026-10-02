package az.innotex.sade;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
public class PayrollController {
    static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final PayrollService svc;

    @GetMapping("/payroll-runs/{id}/payments") public Dto.PaymentsRes payments(@PathVariable Long id) { return svc.payments(id); }
    @PostMapping("/payroll-runs/{id}/payments/link") @ResponseStatus(HttpStatus.CREATED)
    public Dto.PaymentsRes linkPayment(@PathVariable Long id, @RequestBody Dto.PayLinkReq r) { return svc.link(id, r); }
    @DeleteMapping("/payroll-payment-links/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void unlinkPayment(@PathVariable Long id) { svc.unlink(id); }
    @GetMapping("/payroll-runs/{id}/movement-suggestions")
    public List<Dto.MovementSuggestion> movementSuggestions(@PathVariable Long id, @RequestParam String codeKey, @RequestParam(required = false) Long employeeId) { return svc.suggestions(id, codeKey, employeeId); }
    @GetMapping("/payroll-runs/{id}/files") public List<Dto.RunFileRes> runFiles(@PathVariable Long id) { return svc.listFiles(id); }
    @PostMapping("/payroll-runs/{id}/files") @ResponseStatus(HttpStatus.CREATED)
    public Dto.RunFileRes addRunFile(@PathVariable Long id, @RequestParam("file") org.springframework.web.multipart.MultipartFile file, @RequestParam(value = "title", required = false) String title) { return svc.addFile(id, file, title); }
    @DeleteMapping("/payroll-runs/{id}/files/{fileRowId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRunFile(@PathVariable Long id, @PathVariable Long fileRowId) { svc.deleteFile(id, fileRowId); }
    @GetMapping("/payment-codes") public List<Dto.PaymentCodeRes> paymentCodes() { return svc.listCodes(); }
    @PutMapping("/payment-codes/{id}") public Dto.PaymentCodeRes updatePaymentCode(@PathVariable Long id, @RequestBody Dto.PaymentCodeReq r) { return svc.updateCode(id, r); }

    public PayrollController(PayrollService svc) { this.svc = svc; }

    static ResponseEntity<byte[]> download(byte[] data, MediaType type, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(type).body(data);
    }

    @GetMapping("/employees") public List<Dto.EmployeeRes> employees() { return svc.listEmployees(); }
    @PostMapping("/employees") @ResponseStatus(HttpStatus.CREATED) public Dto.EmployeeRes createEmployee(@RequestBody Dto.EmployeeReq r) { return svc.createEmployee(r); }
    @PutMapping("/employees/{id}") public Dto.EmployeeRes updateEmployee(@PathVariable Long id, @RequestBody Dto.EmployeeReq r) { return svc.updateEmployee(id, r); }
    @DeleteMapping("/employees/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteEmployee(@PathVariable Long id) { svc.deleteEmployee(id); }

    @GetMapping("/payroll-runs") public List<Dto.RunSummary> runs() { return svc.listRuns(); }
    @PostMapping("/payroll-runs") @ResponseStatus(HttpStatus.CREATED) public Dto.RunRes createRun(@RequestBody Dto.RunCreateReq r) { return svc.create(r.month()); }
    @GetMapping("/payroll-runs/{id}") public Dto.RunRes run(@PathVariable Long id) { return svc.get(id); }
    @PutMapping("/payroll-runs/{id}/lines/{lineId}")
    public Dto.RunRes updateLine(@PathVariable Long id, @PathVariable Long lineId, @RequestBody Dto.LineReq r) { return svc.updateLine(id, lineId, r.workedDays()); }
    @PostMapping("/payroll-runs/{id}/finalize") public Dto.RunRes finalizeRun(@PathVariable Long id) { return svc.setStatus(id, "FINAL"); }
    @PostMapping("/payroll-runs/{id}/reopen") public Dto.RunRes reopen(@PathVariable Long id) { return svc.setStatus(id, "DRAFT"); }
    @DeleteMapping("/payroll-runs/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteRun(@PathVariable Long id) { svc.delete(id); }

    @GetMapping("/payroll-runs/{id}.xlsx")
    public ResponseEntity<byte[]> xlsx(@PathVariable Long id) {
        return download(svc.xlsx(id), XLSX, "maas-" + svc.run(id).period + ".xlsx");
    }
}
