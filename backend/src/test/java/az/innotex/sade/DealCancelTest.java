package az.innotex.sade;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** Satışın silinməsi, ləğvi və bərpası; məhsulun default ödəniş şərti (2032-ci il: ayrıca dövr) */
class DealCancelTest extends ApiTest {

    private long invoiced(long cust, String price, String date) throws Exception {
        long d = newDeal(cust, price);
        POST("/api/deals/" + d + "/stage", "{\"stage\":\"INVOICE\",\"invoiceNo\":\"S-1\",\"invoiceDate\":\"" + date + "\",\"invoiceAmount\":\"" + price + "\"}");
        return d;
    }

    @Test
    void silmeBaglantisizVeBaglantiIle() throws Exception {
        long acc = newAccount("Silmə bankı", "BANK", "0.00");
        long cust = newCustomer("Silmə Müştərisi");

        // bağlantısız: istənilən mərhələdə silinir; sənəd, tarixçə və yalnız ona aid fayl silinir
        long d1 = invoiced(cust, "100.00", "2032-01-10");
        var up = mvc.perform(multipart("/api/deals/" + d1 + "/documents/upload").file(new MockMultipartFile("file", "m.pdf", "application/pdf", "%PDF-1.4 test".getBytes()))
                .param("code", "CONTRACT").header("Authorization", "Bearer " + token())).andReturn();
        assertThat(up.getResponse().getStatus()).isEqualTo(201);
        long docFile = ((Number) read(up.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8), "$.fileId")).longValue();
        DELETE("/api/deals/" + d1);
        assertThat(run(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/deals/" + d1), null).getResponse().getStatus()).isEqualTo(404);
        assertThat(run(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/files/" + docFile), null).getResponse().getStatus()).isEqualTo(404);

        // bağlantı varsa 409, hərəkət qalır; ayırandan sonra silinir
        long d2 = invoiced(cust, "100.00", "2032-01-11");
        long mov = id(POST("/api/movements", "{\"date\":\"2032-01-12\",\"accountId\":" + acc + ",\"direction\":\"IN\",\"amount\":\"100.00\",\"purpose\":\"CUSTOMER_PAYMENT\"}"));
        POST("/api/deals/" + d2 + "/payments", "{\"movementId\":" + mov + "}");
        assertThat((Boolean) read(GET("/api/deals/" + d2), "$.hasAllocations")).isTrue();
        String body = failDelete("/api/deals/" + d2, 409);
        assertThat((String) read(body, "$.message")).isEqualTo("Əvvəlcə ödəniş bağlantılarını ayırın");
        assertThat((List<?>) read(GET("/api/movements?accountId=" + acc), "$")).hasSize(1);
        long allocId = ((Number) read(GET("/api/deals/" + d2 + "/payments"), "$.items[0].id")).longValue();
        DELETE("/api/payment-allocations/" + allocId);
        DELETE("/api/deals/" + d2);
        assertThat((List<?>) read(GET("/api/movements?accountId=" + acc), "$")).hasSize(1);   // bank hərəkəti toxunulmaz
    }

    @Test
    void legvHesabatdanCixirVeBerpa() throws Exception {
        long acc = newAccount("Ləğv bankı", "BANK", "0.00");
        long cust = newCustomer("Ləğv Müştərisi");
        long keep = invoiced(cust, "500.00", "2032-05-10");
        long wrong = invoiced(cust, "300.00", "2032-05-11");
        assertThat((String) read(GET("/api/reports/quarter?year=2032&q=2"), "$.income")).isEqualTo("800.00");

        int before = read(GET("/api/dashboard"), "$.dealsByStage.INVOICE");
        long mov = id(POST("/api/movements", "{\"date\":\"2032-05-12\",\"accountId\":" + acc + ",\"direction\":\"IN\",\"amount\":\"100.00\",\"purpose\":\"CUSTOMER_PAYMENT\"}"));
        POST("/api/deals/" + wrong + "/payments", "{\"movementId\":" + mov + "}");
        assertThat((List<?>) read(GET("/api/dashboard"), "$.awaitingPayments[?(@.dealId==" + wrong + ")]")).hasSize(1);

        String c = POST("/api/deals/" + wrong + "/cancel", "{\"reason\":\"Yanlış ad\"}");
        assertThat((Boolean) read(c, "$.cancelled")).isTrue();
        assertThat((String) read(c, "$.cancelReason")).isEqualTo("Yanlış ad");
        assertThat((Boolean) read(c, "$.awaitingPayment")).isFalse();
        failPost("/api/deals/" + wrong + "/cancel", "{}", 409);

        // hesabat, panel və siyahıdan çıxır; ödəniş bağlantısı qalır
        assertThat((String) read(GET("/api/reports/quarter?year=2032&q=2"), "$.income")).isEqualTo("500.00");
        assertThat((int) read(GET("/api/dashboard"), "$.dealsByStage.INVOICE")).isEqualTo(before - 1);
        assertThat((List<?>) read(GET("/api/dashboard"), "$.awaitingPayments[?(@.dealId==" + wrong + ")]")).isEmpty();
        assertThat((List<?>) read(GET("/api/deals"), "$[?(@.id==" + wrong + ")]")).isEmpty();
        assertThat((List<?>) read(GET("/api/deals?includeCancelled=true"), "$[?(@.id==" + wrong + ")]")).hasSize(1);
        assertThat((List<?>) read(GET("/api/deals?includeCancelled=true"), "$[?(@.id==" + keep + ")]")).hasSize(1);
        assertThat((List<?>) read(GET("/api/deals/" + wrong + "/payments"), "$.items")).hasSize(1);

        // bərpa
        String r = POST("/api/deals/" + wrong + "/restore", "{}");
        assertThat((Boolean) read(r, "$.cancelled")).isFalse();
        assertThat((Object) read(r, "$.cancelReason")).isNull();
        failPost("/api/deals/" + wrong + "/restore", "{}", 409);
        assertThat((String) read(GET("/api/reports/quarter?year=2032&q=2"), "$.income")).isEqualTo("800.00");
        assertThat((int) read(GET("/api/dashboard"), "$.dealsByStage.INVOICE")).isEqualTo(before);
    }

    @Test
    void mehsulunDefaultOdenisSerti() throws Exception {
        String p = POST("/api/products", "{\"name\":\"Şərt məhsulu\",\"defaultPaymentTerms\":\"PARTIAL\",\"defaultAdvancePercent\":30}");
        assertThat((String) read(p, "$.defaultPaymentTerms")).isEqualTo("PARTIAL");
        assertThat((String) read(p, "$.defaultAdvancePercent")).isEqualTo("30.00");
        failPost("/api/products", "{\"name\":\"Şərt məhsulu 2\",\"defaultPaymentTerms\":\"PARTIAL\"}", 400);
        failPost("/api/products", "{\"name\":\"Şərt məhsulu 3\",\"defaultPaymentTerms\":\"XYZ\"}", 400);
        String q = POST("/api/products", "{\"name\":\"Şərt məhsulu 4\",\"defaultPaymentTerms\":\"PREPAID\"}");
        assertThat((String) read(q, "$.defaultAdvancePercent")).isEqualTo("100.00");
        String n = POST("/api/products", "{\"name\":\"Şərt məhsulu 5\"}");
        assertThat((Object) read(n, "$.defaultPaymentTerms")).isNull();
        PUT("/api/products/" + id(n), "{\"name\":\"Şərt məhsulu 5\",\"defaultPaymentTerms\":\"POSTPAID\"}");
        assertThat(((List<?>) read(GET("/api/products?active=true"), "$[?(@.id==" + id(n) + ")].defaultAdvancePercent")).get(0)).isEqualTo("0.00");
    }
}
