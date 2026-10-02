package az.innotex.sade;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class StatementController {
    private final StatementService svc;
    private final LedgerService ledger;
    private final FileService files;

    public StatementController(StatementService svc, LedgerService ledger, FileService files) { this.svc = svc; this.ledger = ledger; this.files = files; }

    @GetMapping("/accounts/{id}/statements")
    public List<Dto.StatementRes> list(@PathVariable Long id) { return svc.list(id); }

    @PostMapping("/accounts/{id}/statements") @ResponseStatus(HttpStatus.CREATED)
    public Dto.StatementRes create(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                   @RequestParam("periodFrom") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodFrom,
                                   @RequestParam("periodTo") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodTo,
                                   @RequestParam(value = "note", required = false) String note) {
        return svc.create(id, file, periodFrom, periodTo, note);
    }

    @GetMapping("/statements/{id}/file")
    public org.springframework.http.ResponseEntity<byte[]> file(@PathVariable Long id) {
        E.StoredFile f = files.meta(svc.fileIdOf(id));
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, org.springframework.http.ContentDisposition.attachment().filename(f.name, java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .body(files.bytes(f.id));
    }

    @DeleteMapping("/statements/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { svc.delete(id); }

    /** Hərəkəti çıxarışa bağlayır; statementId null olarsa bağlantı silinir (xərcdən yaranan hərəkətlər üçün də işləyir) */
    @PutMapping("/movements/{id}/statement")
    public Dto.MovementRes setStatement(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        return ledger.setMovementStatement(id, body.get("statementId"));
    }
}
