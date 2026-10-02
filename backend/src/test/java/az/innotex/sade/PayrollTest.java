package az.innotex.sade;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PayrollTest extends ApiTest {

    private void employee(String name, String workplace, String gross) throws Exception {
        POST("/api/employees", "{\"name\":\"" + name + "\",\"position\":\"Test\",\"workplace\":\"" + workplace + "\",\"gross\":\"" + gross + "\",\"active\":true}");
    }

    private Map<String, Object> line(String run, String name) {
        List<Map<String, Object>> lines = read(run, "$.lines");
        return lines.stream().filter(l -> name.equals(l.get("name"))).findFirst().orElseThrow();
    }

    private void assertLine(Map<String, Object> l, String income, String dsmfEmp, String medEmp, String unempEmp, String net, String dsmfEr, String medEr, String unempEr, String cost) {
        assertThat(l).containsEntry("income", income).containsEntry("dsmfEmp", dsmfEmp).containsEntry("medEmp", medEmp)
                .containsEntry("unempEmp", unempEmp).containsEntry("net", net).containsEntry("dsmfEr", dsmfEr)
                .containsEntry("medEr", medEr).containsEntry("unempEr", unempEr).containsEntry("employerCost", cost);
    }

    @Test
    void maasHesablamasi() throws Exception {
        employee("P-200", "MAIN", "200.00");
        employee("P-1000", "MAIN", "1000.00");
        employee("P-9000", "MAIN", "9000.00");
        employee("P-1000-elave", "SECONDARY", "1000.00");
        employee("P-yarim", "MAIN", "1000.00");

        String run = POST("/api/payroll-runs", "{\"month\":\"2026-01\"}");
        long runId = id(run);
        assertThat((int) read(run, "$.normDays")).isEqualTo(20);
        assertThat((String) read(run, "$.status")).isEqualTo("DRAFT");

        // 200 AZN: gəlir vergisi 0 (200 azadolma)
        assertLine(line(run, "P-200"), "0.00", "6.00", "4.00", "1.00", "189.00", "44.00", "4.00", "1.00", "249.00");
        // 1000 AZN, əsas iş yeri
        assertLine(line(run, "P-1000"), "24.00", "86.00", "20.00", "5.00", "865.00", "164.00", "20.00", "5.00", "1189.00");
        // 9000 AZN: hədlərdən yuxarı
        assertLine(line(run, "P-9000"), "352.00", "886.00", "165.00", "45.00", "7552.00", "1364.00", "165.00", "45.00", "10574.00");
        // əlavə iş yerində 200 azadolma yoxdur
        assertLine(line(run, "P-1000-elave"), "30.00", "86.00", "20.00", "5.00", "859.00", "164.00", "20.00", "5.00", "1189.00");

        // natamam ay: 20 gündən 10 gün işlənib -> 500 AZN
        long lineId = ((Number) line(run, "P-yarim").get("id")).longValue();
        String upd = PUT("/api/payroll-runs/" + runId + "/lines/" + lineId, "{\"workedDays\":10}");
        Map<String, Object> half = line(upd, "P-yarim");
        assertThat(half).containsEntry("accrued", "500.00").containsEntry("workedDays", 10).containsEntry("gross", "1000.00");
        assertLine(half, "9.00", "36.00", "10.00", "2.50", "442.50", "89.00", "10.00", "2.50", "601.50");

        // cəmlər və siyahı
        assertThat((String) read(upd, "$.totals.net")).isEqualTo("9907.50");
        String list = GET("/api/payroll-runs");
        assertThat(list).contains("\"totalNet\":\"9907.50\"");

        // günlər norma gündən çox ola bilməz
        assertThat(run(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/payroll-runs/" + runId + "/lines/" + lineId), "{\"workedDays\":21}")
                .getResponse().getStatus()).isEqualTo(400);

        // eyni ay üçün təkrar yaratmaq olmaz
        failPost("/api/payroll-runs", "{\"month\":\"2026-01\"}", 409);

        // təsdiqlənəndə dəyişmir, silinmir; yenidən açmaq olar
        POST("/api/payroll-runs/" + runId + "/finalize", "{}");
        assertThat(run(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/payroll-runs/" + runId + "/lines/" + lineId), "{\"workedDays\":5}")
                .getResponse().getStatus()).isEqualTo(409);
        failDelete("/api/payroll-runs/" + runId, 409);

        // Excel ixracı
        var x = run(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/payroll-runs/" + runId + ".xlsx"), null).getResponse();
        assertThat(x.getStatus()).isEqualTo(200);
        assertThat(x.getHeader("Content-Disposition")).contains(".xlsx");
        assertThat(x.getContentAsByteArray()).hasSizeGreaterThan(1000);

        POST("/api/payroll-runs/" + runId + "/reopen", "{}");
        DELETE("/api/payroll-runs/" + runId);
    }

    @Test
    void dereceTarixi() throws Exception {
        String rates = GET("/api/payroll-rates");
        assertThat((String) read(rates, "$[0].validFrom")).isEqualTo("2026-01");
        assertThat((String) read(rates, "$[0].dsmfLimit")).isEqualTo("200.00");
        // iş günü cədvəli
        assertThat((int) read(GET("/api/work-calendar?year=2026"), "$[2].days")).isEqualTo(16);
    }

    @Test
    void bankOdenisleri() throws Exception {
        POST("/api/employees", "{\"name\":\"B-1000\",\"position\":\"Test\",\"workplace\":\"MAIN\",\"gross\":\"1000.00\",\"active\":true,\"bankName\":\"Test Bank\",\"iban\":\"TEST-IBAN\",\"cardNumber\":\"0000\"}");
        String runJson = POST("/api/payroll-runs", "{\"month\":\"2026-09\"}");
        long runId = id(runJson);

        List<Map<String, Object>> codes = read(GET("/api/payment-codes"), "$");
        assertThat(codes).hasSize(8);
        assertThat(codes.get(0)).containsEntry("key", "INCOME_TAX").containsEntry("budgetCode", "111111");
        assertThat(codes.get(7)).containsEntry("key", "NET").containsEntry("budgetCode", null);

        List<Map<String, Object>> ep = read(GET("/api/payroll-runs/" + runId + "/payments"), "$.employees");
        assertThat(ep).anySatisfy(e -> assertThat(e).containsEntry("name", "B-1000").containsEntry("iban", "TEST-IBAN").containsEntry("purpose", "Sentyabr 2026 ayı üzrə əmək haqqı"));
        String pay = GET("/api/payroll-runs/" + runId + "/payments");
        assertThat((int) read(pay, "$.taxes.length()")).isEqualTo(8);
        assertThat((String) read(pay, "$.taxes[0].purpose")).isEqualTo("Sentyabr 2026 ayı üzrə gəlir vergisi");
        assertThat((String) read(pay, "$.taxes[0].amount")).isEqualTo(read(runJson, "$.totals.income"));
        assertThat((String) read(pay, "$.taxes[7].purpose")).isEqualTo("Sentyabr 2026 ayı üzrə əmək haqqı");
        assertThat((String) read(pay, "$.taxes[7].amount")).isEqualTo(read(runJson, "$.totals.net"));

        // passiv kod siyahıdan düşür
        long firstId = ((Number) codes.get(0).get("id")).longValue();
        PUT("/api/payment-codes/" + firstId, "{\"title\":\"Gəlir vergisi (muzdlu iş)\",\"budgetCode\":\"111111\",\"active\":false,\"sortOrder\":1}");
        pay = GET("/api/payroll-runs/" + runId + "/payments");
        assertThat((int) read(pay, "$.taxes.length()")).isEqualTo(7);
        assertThat((int) read(pay, "$.taxes[0].order")).isEqualTo(1);
        assertThat((String) read(pay, "$.taxes[0].key")).isEqualTo("DSMF_EMP");

        var x = run(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/payroll-runs/" + runId + ".xlsx"), null).getResponse();
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(x.getContentAsByteArray()))) {
            assertThat(wb.getSheet("Bank ödənişləri")).isNotNull();
            assertThat(wb.getSheet("Bank ödənişləri").getPhysicalNumberOfRows()).isGreaterThan(9);
        }
    }

    @Test
    void kartBitmeTarixi() throws Exception {
        java.time.YearMonth now = java.time.YearMonth.now();
        String soon = now.plusMonths(1).toString(), past = now.minusMonths(2).toString(), far = now.plusMonths(12).toString();
        String body = "{\"name\":\"%s\",\"position\":\"Test\",\"workplace\":\"MAIN\",\"gross\":\"500.00\",\"active\":true,\"cardNumber\":\"0000\",\"cardExpiry\":%s}";
        String a = POST("/api/employees", String.format(body, "K-soon", "\"" + soon + "\""));
        assertThat((String) read(a, "$.cardExpiry")).isEqualTo(soon);
        POST("/api/employees", String.format(body, "K-past", "\"" + past + "\""));
        POST("/api/employees", String.format(body, "K-far", "\"" + far + "\""));
        POST("/api/employees", String.format(body, "K-none", "null"));
        failPost("/api/employees", String.format(body, "K-bad", "\"2030-13\""), 400);
        List<Map<String, Object>> cards = read(GET("/api/dashboard"), "$.expiringCards");
        List<Object> names = cards.stream().map(c -> c.get("name")).toList();
        assertThat(names).contains("K-soon", "K-past").doesNotContain("K-far", "K-none");
        assertThat(((Number) cards.stream().filter(c -> "K-past".equals(c.get("name"))).findFirst().orElseThrow().get("daysLeft")).longValue()).isNegative();
        assertThat(PUT("/api/employees/" + id(a), String.format(body, "K-soon", "null"))).contains("\"cardExpiry\":null");
    }
}
