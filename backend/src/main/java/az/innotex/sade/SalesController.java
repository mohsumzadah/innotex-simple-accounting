package az.innotex.sade;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SalesController {
    private final SalesService sales;
    private final DocService docs;
    private final PaymentService payments;

    public SalesController(SalesService sales, DocService docs, PaymentService payments) {
        this.payments = payments;
        this.sales = sales;
        this.docs = docs;
    }

    @GetMapping({"/customers", "/counterparties"}) public List<Dto.CustomerDto> customers() { return sales.listCustomers(); }
    @PostMapping({"/customers", "/counterparties"}) @ResponseStatus(HttpStatus.CREATED) public Dto.CustomerDto createCustomer(@RequestBody Dto.CustomerDto r) { return sales.createCustomer(r); }
    @PutMapping({"/customers/{id}", "/counterparties/{id}"}) public Dto.CustomerDto updateCustomer(@PathVariable Long id, @RequestBody Dto.CustomerDto r) { return sales.updateCustomer(id, r); }
    @DeleteMapping({"/customers/{id}", "/counterparties/{id}"}) @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteCustomer(@PathVariable Long id) { sales.deleteCustomer(id); }

    @GetMapping("/products") public List<Dto.ProductRes> products(@RequestParam(required = false) Boolean active) { return sales.listProducts(active); }
    @PostMapping("/products") @ResponseStatus(HttpStatus.CREATED) public Dto.ProductRes createProduct(@RequestBody Dto.ProductReq r) { return sales.createProduct(r); }
    @PutMapping("/products/{id}") public Dto.ProductRes updateProduct(@PathVariable Long id, @RequestBody Dto.ProductReq r) { return sales.updateProduct(id, r); }
    @DeleteMapping("/products/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteProduct(@PathVariable Long id) { sales.deleteProduct(id); }

    @GetMapping("/deals") public List<Dto.DealRes> deals(@RequestParam(required = false) String stage, @RequestParam(required = false, defaultValue = "false") boolean includeCancelled) { return sales.listDeals(stage, includeCancelled); }
    @PostMapping("/deals") @ResponseStatus(HttpStatus.CREATED) public Dto.DealRes createDeal(@RequestBody Dto.DealReq r) { return sales.createDeal(r); }
    @GetMapping("/deals/{id}") public Dto.DealRes deal(@PathVariable Long id) { return sales.getDeal(id); }
    @PutMapping("/deals/{id}") public Dto.DealRes updateDeal(@PathVariable Long id, @RequestBody Dto.DealReq r) { return sales.updateDeal(id, r); }
    @DeleteMapping("/deals/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteDeal(@PathVariable Long id) { sales.deleteDeal(id); }
    @PostMapping("/deals/{id}/cancel") public Dto.DealRes cancelDeal(@PathVariable Long id, @RequestBody(required = false) Dto.CancelReq r) { return sales.cancel(id, r == null ? null : r.reason()); }
    @PostMapping("/deals/{id}/restore") public Dto.DealRes restoreDeal(@PathVariable Long id) { return sales.restore(id); }
    @PostMapping("/deals/{id}/stage") public Dto.DealRes stage(@PathVariable Long id, @RequestBody Dto.StageReq r) { return sales.changeStage(id, r); }
    @PostMapping("/deals/{id}/fields") public Dto.DealRes fields(@PathVariable Long id, @RequestBody Dto.StageReq r) { return sales.saveFields(id, r); }
    @PostMapping("/deals/{id}/complete") public Dto.DealRes complete(@PathVariable Long id, @RequestBody Dto.StageReq r) { return sales.complete(id, r); }
    @PostMapping("/deals/{id}/skip") public Dto.DealRes skip(@PathVariable Long id, @RequestBody(required = false) Dto.StageReq r) { return sales.skip(id, r); }
    @GetMapping("/deals/{id}/events") public List<Dto.EventRes> events(@PathVariable Long id) { return sales.events(id); }

    /** Fiziki şəxsə sürətli satış: multipart, "request" (JSON) + ixtiyari "file" (çek / ekran şəkli / bank tranzaksiyası) */
    @PostMapping(value = "/deals/quick-retail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public Dto.DealRes quickRetail(@RequestPart("request") Dto.QuickRetailReq r, @RequestPart(value = "file", required = false) org.springframework.web.multipart.MultipartFile file) { return sales.quickRetail(r, file); }
    @GetMapping("/payment-suggestions") public List<Dto.PaymentSuggestion> retailSuggestions(@RequestParam(required = false) java.math.BigDecimal amount, @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date, @RequestParam(required = false) String name) { return payments.suggestionsFor(amount, date, name); }

    // ---- satışın ödənişləri (bank mədaxili bağlantıları) ----
    @GetMapping("/deals/{id}/payments") public Dto.DealPayments dealPayments(@PathVariable Long id) { return payments.forDeal(id); }
    @GetMapping("/deals/{id}/payment-suggestions") public List<Dto.PaymentSuggestion> paymentSuggestions(@PathVariable Long id) { return payments.suggestions(id); }
    @PostMapping("/deals/{id}/payments") @ResponseStatus(HttpStatus.CREATED) public Dto.DealPayments addPayment(@PathVariable Long id, @RequestBody Dto.PaymentReq r) { return payments.create(id, r); }
    @PutMapping("/payment-allocations/{id}") public Dto.DealPayments updatePayment(@PathVariable Long id, @RequestBody Dto.PaymentReq r) { return payments.update(id, r); }
    @DeleteMapping("/payment-allocations/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deletePayment(@PathVariable Long id) { payments.delete(id); }

    // ---- satışın sənədləri ----
    @GetMapping("/deals/{id}/documents") public List<Dto.DealDocRes> documents(@PathVariable Long id) { return docs.list(id); }
    @PostMapping("/deals/{id}/documents") @ResponseStatus(HttpStatus.CREATED)
    public Dto.DealDocRes createDocument(@PathVariable Long id, @RequestBody Dto.DealDocCreateReq r) { return docs.create(id, r); }
    @PostMapping("/deals/{id}/documents/upload") @ResponseStatus(HttpStatus.CREATED)
    public Dto.DealDocRes uploadNewDocument(@PathVariable Long id, @RequestParam("code") String code, @RequestParam(value = "title", required = false) String title,
                                            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) { return docs.uploadNew(id, code, title, file); }
    @GetMapping("/deal-documents/{docId}") public Dto.DealDocRes document(@PathVariable Long docId) { return docs.get(docId); }
    @PutMapping("/deal-documents/{docId}") public Dto.DealDocRes updateDocument(@PathVariable Long docId, @RequestBody Dto.DealDocReq r) { return docs.update(docId, r); }
    @DeleteMapping("/deal-documents/{docId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteDocument(@PathVariable Long docId) { docs.delete(docId); }
    @PostMapping("/deal-documents/{docId}/refill") public Dto.DealDocRes refill(@PathVariable Long docId) { return docs.refill(docId); }

    @GetMapping("/deal-documents/{docId}/docx")
    public ResponseEntity<byte[]> docx(@PathVariable Long docId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(docs.docxName(docId), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(docs.contentType(docId)))
                .body(docs.docxBytes(docId));
    }
    @PostMapping("/deal-documents/{docId}/upload") public Dto.DealDocRes upload(@PathVariable Long docId, @RequestParam("file") org.springframework.web.multipart.MultipartFile file) { return docs.upload(docId, file); }
    @PostMapping("/deal-documents/{docId}/revert") public Dto.DealDocRes revert(@PathVariable Long docId) { return docs.revert(docId); }

    @GetMapping("/deal-documents/{docId}/print")
    public ResponseEntity<String> print(@PathVariable Long docId) {
        return ResponseEntity.ok().contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8)).body(docs.print(docId));
    }
}
