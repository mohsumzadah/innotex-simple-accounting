package az.innotex.sade;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** Maaş cədvəli bank ödənişləri: bağlantı, status, təkliflər, sənədlər, idarə paneli (2026-10: ayrıca dövr) */
class PayrollPaymentsTest extends ApiTest {

    private long mov(long acc, String dir, String date, String amount, String purpose, String note) throws Exception {
        return id(POST("/api/movements", "{\"date\":\"" + date + "\",\"accountId\":" + acc + ",\"direction\":\"" + dir + "\",\"amount\":\"" + amount + "\",\"purpose\":\"" + purpose + "\""
                + (note == null ? "" : ",\"note\":\"" + note + "\"") + "}"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> tax(String pay, String key) {
        List<Map<String, Object>> l = read(pay, "$.taxes");
        return l.stream().filter(x -> key.equals(x.get("key"))).findFirst().orElseThrow();
    }

    private MvcResult up(String url, MockMultipartFile f, String... params) throws Exception {
        var b = multipart(url).file(f).header("Authorization", "Bearer " + token());
        for (int i = 0; i < params.length; i += 2) b.param(params[i], params[i + 1]);
        return mvc.perform(b).andReturn();
    }

    private List<Long> sugIds(long runId, String key) throws Exception {
        List<Map<String, Object>> sg = read(GET("/api/payroll-runs/" + runId + "/movement-suggestions?codeKey=" + key), "$");
        return sg.stream().map(x -> ((Number) x.get("movementId")).longValue()).toList();
    }

    @Test
    @SuppressWarnings("unchecked")
    void baglantiStatusTekliflerVeSenedler() throws Exception {
        long empId = id(POST("/api/employees", "{\"name\":\"PP-1000\",\"position\":\"Test\",\"workplace\":\"MAIN\",\"gross\":\"1000.00\",\"active\":true}"));
        long acc = newAccount("Maaş bankı", "BANK", "0.00");
        long card = newAccount("Maaş kartı", "DIRECTOR_CARD", "0.00");
        String runJson = POST("/api/payroll-runs", "{\"month\":\"2026-10\"}");
        long runId = id(runJson);
        String income = read(runJson, "$.totals.income");
        String net = read(runJson, "$.totals.net");
        POST("/api/payroll-runs/" + runId + "/finalize", "{}");

        // başlanğıcda UNPAID, idarə panelində xəbərdarlıq
        String pay = GET("/api/payroll-runs/" + runId + "/payments");
        assertThat(tax(pay, "INCOME_TAX")).containsEntry("status", "UNPAID").containsEntry("paidAmount", "0.00");
        assertThat((List<?>) read(GET("/api/dashboard"), "$.unpaidPayroll[?(@.runId==" + runId + ")]")).hasSize(1);

        long exact = mov(acc, "OUT", "2026-11-02", income, "TAX", "Gəlir vergisi");
        long other = mov(acc, "OUT", "2026-11-02", "0.07", "TAX", null);
        long old = mov(acc, "OUT", "2025-01-02", income, "TAX", null);
        long cardMov = mov(card, "OUT", "2026-11-02", income, "TAX", null);
        long wrongPurpose = mov(acc, "OUT", "2026-11-02", income, "EXPENSE", null);
        long inMov = mov(acc, "IN", "2026-11-02", income, "OTHER", null);

        // sıralama: dəqiq məbləğ birinci; kart hesabı, yanlış təyinat və IN yoxdur; dövrdən kənar məbləğ-uyğun hərəkət dövr daxilində başqa məbləğdən yuxarıdır (100 > 30+20)
        List<Long> ids = sugIds(runId, "INCOME_TAX");
        assertThat(ids.get(0)).isEqualTo(exact);
        assertThat(ids).contains(other, old).doesNotContain(cardMov, wrongPurpose, inMov);
        assertThat(ids.indexOf(old)).isLessThan(ids.indexOf(other));
        List<Map<String, Object>> sg = read(GET("/api/payroll-runs/" + runId + "/movement-suggestions?codeKey=INCOME_TAX"), "$");
        assertThat((Integer) sg.get(0).get("score")).isGreaterThan((Integer) sg.get(1).get("score"));
        assertThat((List<String>) sg.get(0).get("reasons")).contains("məbləğ eynidir");

        // bağla (məbləğ default) -> PAID; bağlı hərəkət təklifdən çıxır
        String linked = POST("/api/payroll-runs/" + runId + "/payments/link", "{\"codeKey\":\"INCOME_TAX\",\"movementId\":" + exact + "}");
        assertThat(tax(linked, "INCOME_TAX")).containsEntry("status", "PAID").containsEntry("paidAmount", income);
        Map<String, Object> link0 = ((List<Map<String, Object>>) tax(linked, "INCOME_TAX").get("links")).get(0);
        long linkId = ((Number) link0.get("id")).longValue();
        assertThat(link0).containsEntry("date", "2026-11-02");
        assertThat(sugIds(runId, "INCOME_TAX")).doesNotContain(exact);
        failPost("/api/payroll-runs/" + runId + "/payments/link", "{\"codeKey\":\"INCOME_TAX\",\"movementId\":" + old + "}", 409);   // sətir artıq ödənilib

        // qismən: NET-in yarısı
        String half = new java.math.BigDecimal(net).divide(new java.math.BigDecimal("2")).setScale(2, java.math.RoundingMode.DOWN).toPlainString();
        long salary = mov(acc, "OUT", "2026-10-31", half, "SALARY", "Maaş");
        String p2 = POST("/api/payroll-runs/" + runId + "/payments/link", "{\"codeKey\":\"NET\",\"movementId\":" + salary + "}");
        assertThat(tax(p2, "NET")).containsEntry("status", "PARTIAL").containsEntry("paidAmount", half);
        failPost("/api/payroll-runs/" + runId + "/payments/link", "{\"codeKey\":\"NET\",\"movementId\":" + salary + ",\"amount\":\"1.00\"}", 409);   // hərəkətin qalığı yoxdur
        failPost("/api/payroll-runs/" + runId + "/payments/link", "{\"codeKey\":\"XYZ\",\"movementId\":" + salary + "}", 400);
        failPost("/api/payroll-runs/" + runId + "/payments/link", "{\"codeKey\":\"DSMF_EMP\",\"movementId\":" + inMov + "}", 400);   // IN bağlanmır

        // ayır -> yenidən UNPAID; təkrar ayırma 404
        DELETE("/api/payroll-payment-links/" + linkId);
        assertThat(tax(GET("/api/payroll-runs/" + runId + "/payments"), "INCOME_TAX")).containsEntry("status", "UNPAID");
        assertThat(run(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/payroll-payment-links/" + linkId), null).getResponse().getStatus()).isEqualTo(404);

        // işçi üzrə NET
        String p3 = POST("/api/payroll-runs/" + runId + "/payments/link", "{\"codeKey\":\"NET\",\"employeeId\":" + empId + ",\"movementId\":" + old + ",\"amount\":\"0.50\"}");
        List<Map<String, Object>> emps = read(p3, "$.employees");
        assertThat(emps.stream().filter(e -> "PP-1000".equals(e.get("name"))).findFirst().orElseThrow()).containsEntry("status", "PARTIAL").containsEntry("paidAmount", "0.50");

        // sənədlər
        MockMultipartFile f = new MockMultipartFile("file", "qebz.pdf", "application/pdf", "PDF-test".getBytes());
        MvcResult r = up("/api/payroll-runs/" + runId + "/files", f, "title", "Maaş qəbzi");
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        String fr = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat((String) read(fr, "$.title")).isEqualTo("Maaş qəbzi");
        long fileId = ((Number) read(fr, "$.fileId")).longValue();
        assertThat((List<?>) read(GET("/api/payroll-runs/" + runId + "/files"), "$")).hasSize(1);
        assertThat(run(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/files/" + fileId), null).getResponse().getContentAsByteArray()).isEqualTo("PDF-test".getBytes());
        DELETE("/api/payroll-runs/" + runId + "/files/" + id(fr));
        assertThat((List<?>) read(GET("/api/payroll-runs/" + runId + "/files"), "$")).isEmpty();
        assertThat(run(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/files/" + fileId), null).getResponse().getStatus()).isEqualTo(404);

        PUT("/api/employees/" + empId, "{\"name\":\"PP-1000\",\"position\":\"Test\",\"workplace\":\"MAIN\",\"gross\":\"1000.00\",\"active\":false}");
    }
}
