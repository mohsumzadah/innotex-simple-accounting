package az.innotex.sade;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Mərkəzi Bank (CBAR) məzənnələri: tarix üzrə bir dəfə gətirilir və bazada saxlanır */
@Service
public class FxService {
    private final R.FxRates rates;
    private final CbarClient cbar;

    public FxService(R.FxRates rates, CbarClient cbar) {
        this.rates = rates;
        this.cbar = cbar;
    }

    @Transactional
    public Dto.FxRes get(LocalDate date, String currency) {
        if (date == null) throw ApiException.bad("Tarix göstərilməlidir");
        String cur = currency == null ? "" : currency.trim().toUpperCase();
        if (cur.isEmpty()) throw ApiException.bad("Valyuta göstərilməlidir");
        if ("AZN".equals(cur)) return new Dto.FxRes(date, "AZN", "1.0000", "AZN", true);
        if (date.isAfter(LocalDate.now())) throw ApiException.bad("Gələcək tarix üçün məzənnə yoxdur");
        E.FxRate hit = rates.findById(new E.FxRate.Key(date, cur)).orElse(null);
        if (hit != null) return toDto(hit, true);
        Map<String, BigDecimal> all;
        try {
            all = parse(cbar.fetchXml(date));
        } catch (Exception ex) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "cbar_unavailable", "Mərkəzi Bankdan məzənnə alınmadı, əl ilə daxil edin");
        }
        if (all.isEmpty()) throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "cbar_unavailable", "Mərkəzi Bankdan məzənnə alınmadı, əl ilə daxil edin");
        LocalDateTime now = LocalDateTime.now();
        all.forEach((c, v) -> {
            if (rates.existsById(new E.FxRate.Key(date, c))) return;
            E.FxRate r = new E.FxRate();
            r.rateDate = date; r.currency = c; r.rate = v; r.source = "CBAR"; r.fetchedAt = now;
            rates.save(r);
        });
        E.FxRate found = rates.findById(new E.FxRate.Key(date, cur)).orElseThrow(() -> ApiException.notFound("Bu valyuta üçün Mərkəzi Bank məzənnəsi tapılmadı"));
        return toDto(found, false);
    }

    private static Dto.FxRes toDto(E.FxRate r, boolean cached) {
        return new Dto.FxRes(r.rateDate, r.currency, r.rate.setScale(4, RoundingMode.HALF_UP).toPlainString(), r.source, cached);
    }

    /** ValCurs > ValType[Xarici valyutalar] > Valute[Code] > Nominal, Value; məzənnə = Value / Nominal */
    static Map<String, BigDecimal> parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        NodeList types = doc.getElementsByTagName("ValType");
        for (int i = 0; i < types.getLength(); i++) {
            Element t = (Element) types.item(i);
            if (!"Xarici valyutalar".equals(t.getAttribute("Type"))) continue;
            NodeList vs = t.getElementsByTagName("Valute");
            for (int j = 0; j < vs.getLength(); j++) {
                Element v = (Element) vs.item(j);
                String code = v.getAttribute("Code").trim().toUpperCase();
                if (code.length() != 3) continue;
                BigDecimal nominal = new BigDecimal(text(v, "Nominal").replaceAll("[^0-9.]", ""));
                BigDecimal value = new BigDecimal(text(v, "Value").trim().replace(',', '.'));
                if (nominal.signum() <= 0 || value.signum() <= 0) continue;
                out.put(code, value.divide(nominal, 4, RoundingMode.HALF_UP));
            }
        }
        return out;
    }

    private static String text(Element e, String tag) { return e.getElementsByTagName(tag).item(0).getTextContent(); }
}
