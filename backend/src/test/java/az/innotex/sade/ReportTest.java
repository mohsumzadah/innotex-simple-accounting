package az.innotex.sade;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** 2030-cu il: digər testlərlə toqquşmamaq üçün ayrıca dövr */
class ReportTest extends ApiTest {

    private void invoice(long deal, String date, String amount) throws Exception {
        POST("/api/deals/" + deal + "/stage", "{\"stage\":\"INVOICE\",\"invoiceNo\":\"T-1\",\"invoiceDate\":\"" + date + "\",\"invoiceAmount\":\"" + amount + "\"}");
    }

    @Test
    void ruebluekHesabat() throws Exception {
        long acc = newAccount("Hesabat bankı", "BANK", "0.00");
        long cust = newCustomer("Hesabat Müştərisi");

        // gəlir yalnız yekun qaimə tarixi ilə; avans daxil deyil
        long d1 = newDeal(cust, "1000.00");
        invoice(d1, "2030-05-10", "1000.00");
        long d2 = newDeal(cust, "700.00");
        POST("/api/deals/" + d2 + "/stage", "{\"stage\":\"ADVANCE_INVOICE\",\"advanceInvoiceDate\":\"2030-05-12\",\"advanceAmount\":\"700.00\"}");
        long d3 = newDeal(cust, "200.00");
        invoice(d3, "2030-08-01", "200.00");   // başqa rüb

        POST("/api/expenses", "{\"date\":\"2030-05-11\",\"category\":\"Abunəlik\",\"amount\":\"300.00\",\"accountId\":" + acc + ",\"deductible\":true}");
        POST("/api/expenses", "{\"date\":\"2030-05-12\",\"category\":\"Digər\",\"amount\":\"100.00\",\"accountId\":" + acc + ",\"deductible\":false}");
        POST("/api/movements", "{\"date\":\"2030-05-13\",\"accountId\":" + acc + ",\"direction\":\"OUT\",\"amount\":\"20.00\",\"purpose\":\"BANK_FEE\"}");

        String q2 = GET("/api/reports/quarter?year=2030&q=2");
        assertThat((String) read(q2, "$.income")).isEqualTo("1000.00");
        assertThat((java.util.List<?>) read(q2, "$.incomeItems")).hasSize(1);
        assertThat((String) read(q2, "$.bankFees")).isEqualTo("20.00");
        assertThat((String) read(q2, "$.payrollCost")).isEqualTo("0.00");
        assertThat((String) read(q2, "$.expenses")).isEqualTo("320.00");       // 300 + 20 (çıxılmayan 100 daxil deyil)
        assertThat((String) read(q2, "$.expenseByCategory[0].category")).isEqualTo("Abunəlik");
        assertThat((String) read(q2, "$.profit")).isEqualTo("680.00");
        assertThat((String) read(q2, "$.profitTaxRate")).isEqualTo("20.00");
        assertThat((String) read(q2, "$.profitTax")).isEqualTo("136.00");
        assertThat((String) read(q2, "$.period.from")).isEqualTo("2030-04-01");
        assertThat((String) read(q2, "$.period.to")).isEqualTo("2030-06-30");

        // zərərdə vergi 0
        POST("/api/expenses", "{\"date\":\"2030-11-05\",\"category\":\"Digər\",\"amount\":\"500.00\",\"accountId\":" + acc + "}");
        String q4 = GET("/api/reports/quarter?year=2030&q=4");
        assertThat((String) read(q4, "$.profit")).isEqualTo("-500.00");
        assertThat((String) read(q4, "$.profitTax")).isEqualTo("0.00");

        // illik: rüblər üzrə və cəmi
        String y = GET("/api/reports/year?year=2030");
        assertThat((String) read(y, "$.income")).isEqualTo("1200.00");
        assertThat((java.util.List<?>) read(y, "$.quarters")).hasSize(4);
        assertThat((String) read(y, "$.quarters[1].profitTax")).isEqualTo("136.00");
        assertThat((String) read(y, "$.quarters[3].profitTax")).isEqualTo("0.00");

        // Excel ixracı
        var x = run(get("/api/reports/quarter.xlsx?year=2030&q=2"), null).getResponse();
        assertThat(x.getStatus()).isEqualTo(200);
        assertThat(x.getHeader("Content-Disposition")).contains("filename");
        assertThat(x.getContentAsByteArray()).hasSizeGreaterThan(1000);
        assertThat(run(get("/api/reports/year.xlsx?year=2030"), null).getResponse().getStatus()).isEqualTo(200);
        assertThat(run(get("/api/reports/quarter?year=2030&q=5"), null).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void idarePaneli() throws Exception {
        String d = GET("/api/dashboard");
        assertThat((Object) read(d, "$.quarter.year")).isNotNull();
        assertThat((java.util.Map<String, Object>) read(d, "$.dealsByStage")).containsKeys("NEW", "CONTRACT", "PAID", "DONE");
        assertThat((String) read(d, "$.ownerDebt")).matches("-?\\d+\\.\\d{2}");
    }
}
