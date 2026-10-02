package az.innotex.sade;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class DocumentsTest extends ApiTest {

    @Test
    void mebleqSozle() {
        assertThat(Words.number(0)).isEqualTo("sıfır");
        assertThat(Words.number(21)).isEqualTo("iyirmi bir");
        assertThat(Words.number(100)).isEqualTo("yüz");
        assertThat(Words.number(115)).isEqualTo("yüz on beş");
        assertThat(Words.number(200)).isEqualTo("iki yüz");
        assertThat(Words.number(1000)).isEqualTo("min");
        assertThat(Words.number(2026)).isEqualTo("iki min iyirmi altı");
        assertThat(Words.number(1_000_000)).isEqualTo("bir milyon");
        assertThat(Words.amount(new BigDecimal("100"))).isEqualTo("yüz manat 00 qəpik");
        assertThat(Words.amount(new BigDecimal("1250.5"))).isEqualTo("min iki yüz əlli manat 50 qəpik");
        assertThat(Words.amount(new BigDecimal("0.07"))).isEqualTo("sıfır manat 07 qəpik");
    }

    @Test
    void sablonlarVeDefault() throws Exception {
        for (String code : List.of("CONTRACT", "PROTOCOL", "ACT")) {
            List<Map<String, Object>> l = read(GET("/api/templates?code=" + code), "$");
            assertThat(l).isNotEmpty();
            assertThat(l.stream().filter(t -> Boolean.TRUE.equals(t.get("isDefault")))).hasSize(1);
        }
        // yeni şablon default olanda köhnəsi default olmur
        String neu = POST("/api/templates", "{\"code\":\"ACT\",\"title\":\"Test akt\",\"html\":\"<p>{{customer.name}}</p>\",\"isDefault\":false}");
        long newId = id(neu);
        assertThat((Boolean) read(neu, "$.isDefault")).isFalse();
        String upd = PUT("/api/templates/" + newId, "{\"id\":" + newId + ",\"code\":\"ACT\",\"title\":\"Test akt\",\"html\":\"<p>{{customer.name}}</p>\",\"isDefault\":true}");
        assertThat((Boolean) read(upd, "$.isDefault")).isTrue();
        List<Map<String, Object>> acts = read(GET("/api/templates?code=ACT"), "$");
        assertThat(acts.stream().filter(t -> Boolean.TRUE.equals(t.get("isDefault")))).hasSize(1);
        assertThat(GET("/api/templates")).contains("CONTRACT").contains("PROTOCOL");
        // default şablonu geri qaytar, sonra sil
        long oldDefault = acts.stream().filter(t -> !((Number) t.get("id")).equals(newId)).map(t -> ((Number) t.get("id")).longValue()).findFirst().orElseThrow();
        PUT("/api/templates/" + oldDefault, "{\"code\":\"ACT\",\"title\":\"" + read(GET("/api/templates/" + oldDefault), "$.title") + "\",\"html\":"
                + quote((String) read(GET("/api/templates/" + oldDefault), "$.html")) + ",\"isDefault\":true}");
        DELETE("/api/templates/" + newId);
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\"";
    }

    @Test
    void senedSablondanYaranirSablonDeyismir() throws Exception {
        // uydurma rekvizitlər (real məlumat yox)
        PUT("/api/settings", "{\"companyName\":\"Test Təchizatçı MMC\",\"voen\":\"1111111111\",\"address\":\"Test ünvan 1\",\"director\":\"Test Direktor\","
                + "\"bank\":\"Test Bank\",\"iban\":\"AZ00TEST0000000000000000000\",\"bankCode\":\"000000\",\"swift\":\"TESTAZ2X\",\"phone\":\"+994000000000\","
                + "\"email\":\"test@example.az\",\"profitTaxRate\":\"20.00\"}");
        assertThat((String) read(GET("/api/settings"), "$.profitTaxRate")).isEqualTo("20.00");

        long cust = id(POST("/api/customers", "{\"name\":\"Test Müştəri <B> MMC\",\"voen\":\"2222222222\",\"director\":\"Test Sifarişçi\"}"));
        long deal = newDeal(cust, "1250.50");
        POST("/api/deals/" + deal + "/stage", "{\"stage\":\"CONTRACT\",\"contractNo\":\"\",\"contractDate\":\"2026-03-05\"}");
        String contractNo = read(GET("/api/deals/" + deal), "$.contractNo");

        String templateBefore = GET("/api/templates?code=CONTRACT");
        long tplId = ((Number) read(templateBefore, "$[0].id")).longValue();
        String tplHtml = read(GET("/api/templates/" + tplId), "$.html");
        assertThat(tplHtml).contains("{{customer.name}}").contains("{{deal.priceWords}}").doesNotContain("Test Müştəri");

        // sənəd yaradılır: placeholder-lər dolur
        String doc = POST("/api/deals/" + deal + "/documents", "{\"code\":\"CONTRACT\",\"templateId\":" + tplId + "}");
        long docId = id(doc);
        String html = read(doc, "$.html");
        assertThat(html).doesNotContain("{{");
        assertThat(html).contains("Test Təchizatçı MMC").contains("1111111111").contains("Test Müştəri &lt;B&gt; MMC").contains("2222222222")
                .contains(contractNo).contains("05.03.2026").contains("1250.50 AZN (min iki yüz əlli manat 50 qəpik)")
                .contains("maksimum 2 kompüterdə");
        assertThat((String) read(doc, "$.code")).isEqualTo("CONTRACT");
        assertThat((String) read(doc, "$.updatedAt")).isNotBlank();

        // sənədi redaktə edirik: bənd əlavə olunur
        String edited = html.replace("<h2>13.", "<p>14. ƏLAVƏ BƏND (test)</p><h2>13.");
        String putRes = PUT("/api/deal-documents/" + docId, "{\"title\":\"Müqavilə (redaktə)\",\"html\":" + quote(edited) + "}");
        assertThat((String) read(putRes, "$.html")).contains("ƏLAVƏ BƏND");
        assertThat((String) read(GET("/api/deal-documents/" + docId), "$.title")).isEqualTo("Müqavilə (redaktə)");

        // şablon dəyişməyib
        assertThat((String) read(GET("/api/templates/" + tplId), "$.html")).isEqualTo(tplHtml);

        // satış məlumatı dəyişəndən sonra yenidən doldurma əl dəyişikliklərini silir
        PUT("/api/deals/" + deal, "{\"customerId\":" + cust + ",\"product\":\"INNOTEX e-Qaimə\",\"description\":\"\",\"price\":\"100.00\",\"computers\":3,\"note\":\"\"}");
        String refilled = POST("/api/deal-documents/" + docId + "/refill", "{}");
        assertThat((String) read(refilled, "$.html")).doesNotContain("ƏLAVƏ BƏND").contains("100.00 AZN (yüz manat 00 qəpik)").contains("maksimum 3 kompüterdə");
        assertThat(id(refilled)).isEqualTo(docId);

        // siyahı və çap
        assertThat((List<?>) read(GET("/api/deals/" + deal + "/documents"), "$")).hasSize(1);
        var print = run(get("/api/deal-documents/" + docId + "/print"), null).getResponse();
        assertThat(print.getStatus()).isEqualTo(200);
        assertThat(print.getContentType()).startsWith("text/html");
        assertThat(print.getContentAsString()).contains("@page").contains("Test Təchizatçı MMC");

        // protokol və akt eyni mexanizmlə; boş tarix çap xətti olur
        String prot = POST("/api/deals/" + deal + "/documents", "{\"code\":\"PROTOCOL\"}");
        assertThat((String) read(prot, "$.html")).doesNotContain("{{").contains("Test Müştəri &lt;B&gt; MMC").contains("100.00");
        String act = POST("/api/deals/" + deal + "/documents", "{\"code\":\"ACT\"}");
        assertThat((String) read(act, "$.html")).doesNotContain("{{").contains("__________");
        failPost("/api/deals/" + deal + "/documents", "{\"code\":\"YANLIS\"}", 400);

        // köhnə yollar ləğv olunub
        assertThat(run(get("/api/deals/" + deal + "/documents/CONTRACT"), null).getResponse().getStatus()).isIn(404, 405);

        DELETE("/api/deal-documents/" + docId);
        assertThat((List<?>) read(GET("/api/deals/" + deal + "/documents"), "$")).hasSize(2);
    }

    @Test
    void bosSetirlerNullSayilir() throws Exception {
        long acc = id(POST("/api/accounts", "{\"name\":\"Boş test\",\"type\":\"CASH\",\"openingBalance\":\"0.00\",\"openingDate\":\"\",\"currency\":\"AZN\"}"));
        List<Map<String, Object>> accs = read(GET("/api/accounts"), "$");
        assertThat(accs.stream().filter(a -> ((Number) a.get("id")).longValue() == acc).findFirst().orElseThrow().get("openingDate")).isNull();
        String rate = POST("/api/payroll-rates", "{\"validFrom\":\"2027-01\",\"dsmfLimit\":\"200.00\",\"dsmfEmpLow\":\"3.00\",\"dsmfEmpHigh\":\"10.00\","
                + "\"dsmfErLow\":\"22.00\",\"dsmfErHigh\":\"15.00\",\"unempEmp\":\"0.50\",\"unempEr\":\"0.50\",\"medLimit\":\"8000.00\",\"medLow\":\"2.00\","
                + "\"medHigh\":\"0.50\",\"incomeLimit\":\"8000.00\",\"incomeExempt\":\"200.00\",\"incomeLow\":\"3.00\",\"incomeHigh\":\"14.00\"}");
        assertThat((String) read(rate, "$.unempEmp")).isEqualTo("0.50");
        DELETE("/api/payroll-rates/" + id(rate));
        PUT("/api/work-calendar?year=2031", "[{\"month\":\"2031-01\",\"days\":21}]");
        assertThat((int) read(GET("/api/work-calendar?year=2031"), "$[0].days")).isEqualTo(21);
    }
}
