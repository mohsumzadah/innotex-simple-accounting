package az.innotex.sade;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SettingsController {
    private final SettingsService svc;

    public SettingsController(SettingsService svc) { this.svc = svc; }

    @GetMapping("/settings") public Dto.SettingsRes settings() { return svc.get(); }
    @PutMapping("/settings") public Dto.SettingsRes putSettings(@RequestBody Dto.SettingsReq r) { return svc.put(r); }

    @GetMapping("/payroll-rates") public List<Dto.RateRes> rates() { return svc.listRates(); }
    @PostMapping("/payroll-rates") @ResponseStatus(HttpStatus.CREATED) public Dto.RateRes createRate(@RequestBody Dto.RateReq r) { return svc.createRate(r); }
    @PutMapping("/payroll-rates/{id}") public Dto.RateRes updateRate(@PathVariable Long id, @RequestBody Dto.RateReq r) { return svc.updateRate(id, r); }
    @DeleteMapping("/payroll-rates/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteRate(@PathVariable Long id) { svc.deleteRate(id); }

    @GetMapping("/work-calendar") public List<Dto.CalendarItem> calendar(@RequestParam int year) { return svc.calendar(year); }
    @PutMapping("/work-calendar") public List<Dto.CalendarItem> putCalendar(@RequestParam int year, @RequestBody List<Dto.CalendarItem> items) { return svc.putCalendar(year, items); }

    @GetMapping("/templates") public List<Dto.TemplateRes> templates(@RequestParam(required = false) String code) { return svc.listTemplates(code); }
    @PostMapping("/templates") @ResponseStatus(HttpStatus.CREATED) public Dto.TemplateRes createTemplate(@RequestBody Dto.TemplateReq r) { return svc.createTemplate(r); }
    @GetMapping("/templates/{id}") public Dto.TemplateRes template(@PathVariable Long id) { return svc.getTemplate(id); }
    @PutMapping("/templates/{id}") public Dto.TemplateRes putTemplate(@PathVariable Long id, @RequestBody Dto.TemplateReq r) { return svc.putTemplate(id, r); }
    @DeleteMapping("/templates/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteTemplate(@PathVariable Long id) { svc.deleteTemplate(id); }
}
