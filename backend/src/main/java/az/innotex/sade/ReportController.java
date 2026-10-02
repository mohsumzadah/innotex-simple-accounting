package az.innotex.sade;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ReportController {
    private final ReportService svc;

    public ReportController(ReportService svc) { this.svc = svc; }

    @GetMapping("/dashboard") public Dto.Dashboard dashboard() { return svc.dashboard(); }

    @GetMapping("/reports/quarter") public Dto.Report quarter(@RequestParam int year, @RequestParam int q) { return svc.quarter(year, q); }
    @GetMapping("/reports/year") public Dto.Report year(@RequestParam int year) { return svc.year(year); }

    @GetMapping("/reports/quarter.xlsx")
    public ResponseEntity<byte[]> quarterXlsx(@RequestParam int year, @RequestParam int q) {
        return PayrollController.download(svc.xlsx(svc.quarter(year, q), "Rüblük hesabat " + year + " Q" + q), PayrollController.XLSX, "hesabat-" + year + "-Q" + q + ".xlsx");
    }

    @GetMapping("/reports/year.xlsx")
    public ResponseEntity<byte[]> yearXlsx(@RequestParam int year) {
        return PayrollController.download(svc.xlsx(svc.year(year), "İllik hesabat " + year), PayrollController.XLSX, "hesabat-" + year + ".xlsx");
    }
}
