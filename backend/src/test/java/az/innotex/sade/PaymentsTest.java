package az.innotex.sade;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Ödəniş şərtləri və bank mədaxilinin satışlara bağlanması (payment_allocation) */
class PaymentsTest extends ApiTest {
    @Autowired R.Deals dealRepo;
    @Autowired R.Movements movementRepo;
    @Autowired JdbcTemplate jdbc;

    private long newMovement(long acc, String amount, String purpose) throws Exception {
        return id(POST("/api/movements", "{\"date\":\"2026-06-01\",\"accountId\":" + acc + ",\"direction\":\"IN\",\"amount\":\"" + amount + "\",\"purpose\":\"" + purpose + "\"}"));
    }

    private long dealWith(long cust, String price, String terms, String percent) throws Exception {
        return id(POST("/api/deals", "{\"customerId\":" + cust + ",\"price\":\"" + price + "\",\"computers\":1,\"paymentTerms\":\"" + terms + "\""
                + (percent == null ? "" : ",\"advancePercent\":" + percent) + "}"));
    }

    private Map<String, Object> step(String deal, String key) {
        List<Map<String, Object>> st = read(deal, "$.stepper");
        return st.stream().filter(x -> key.equals(x.get("key"))).findFirst().orElseThrow();
    }

    private List<String> keys(String deal) {
        List<Map<String, Object>> st = read(deal, "$.stepper");
        return st.stream().map(x -> (String) x.get("key")).toList();
    }

    @Test
    void qismenAvans50Faiz() throws Exception {
        long acc = newAccount("Ödəniş bank 1", "BANK", "0.00");
        long cust = newCustomer("Qismən Müştəri");
        failPost("/api/deals", "{\"customerId\":" + cust + ",\"price\":\"10.00\",\"paymentTerms\":\"PARTIAL\",\"advancePercent\":0}", 400);
        failPost("/api/deals", "{\"customerId\":" + cust + ",\"price\":\"10.00\",\"paymentTerms\":\"PARTIAL\"}", 400);
        failPost("/api/deals", "{\"customerId\":" + cust + ",\"price\":\"10.00\",\"paymentTerms\":\"XYZ\"}", 400);
        long deal = dealWith(cust, "200.00", "PARTIAL", "50");
        String d = GET("/api/deals/" + deal);
        assertThat((String) read(d, "$.paymentTerms")).isEqualTo("PARTIAL");
        assertThat((String) read(d, "$.advanceRequired")).isEqualTo("100.00");
        assertThat((String) read(d, "$.paymentStatus")).isEqualTo("UNPAID");
        assertThat(keys(d)).containsExactly("NEW", "CONTRACT", "PROTOCOL", "ADVANCE_INVOICE", "PAID", "IN_PROGRESS", "ACT", "INVOICE", "FINAL_PAYMENT", "DONE");
        assertThat(step(d, "PAID")).containsEntry("label", "Avans ödənişi").containsEntry("done", false);
        assertThat(step(d, "NEW")).containsEntry("current", true);

        // avans qaiməsi: məbləğ default qiymət × faiz
        String a = POST("/api/deals/" + deal + "/stage", "{\"stage\":\"ADVANCE_INVOICE\"}");
        assertThat((String) read(a, "$.advanceAmount")).isEqualTo("100.00");

        // avans bağlantısı (kind default = ADVANCE): avans addımı done, qalıq hələ yox
        long m1 = newMovement(acc, "100.00", "CUSTOMER_PAYMENT");
        String p1 = POST("/api/deals/" + deal + "/payments", "{\"movementId\":" + m1 + "}");
        assertThat((String) read(p1, "$.items[0].kind")).isEqualTo("ADVANCE");
        assertThat((String) read(p1, "$.advancePaid")).isEqualTo("100.00");
        assertThat((String) read(p1, "$.status")).isEqualTo("PARTIAL");
        assertThat((String) read(p1, "$.remaining")).isEqualTo("100.00");
        String d1 = GET("/api/deals/" + deal);
        assertThat(step(d1, "PAID")).containsEntry("done", true);
        assertThat(step(d1, "FINAL_PAYMENT")).containsEntry("done", false);
        assertThat(step(d1, "ADVANCE_INVOICE")).containsEntry("current", true);
        assertThat((String) read(d1, "$.paid")).isEqualTo("100.00");

        // qaimə mərhələsində qalıq gözlənilir (idarə paneli)
        POST("/api/deals/" + deal + "/stage", "{\"stage\":\"INVOICE\"}");
        assertThat((Boolean) read(GET("/api/deals/" + deal), "$.awaitingPayment")).isTrue();
        assertThat((List<?>) read(GET("/api/dashboard"), "$.awaitingPayments[?(@.dealId==" + deal + ")]")).hasSize(1);

        // qalıq: kind default FINAL, məbləğ default = satışın qalığı
        long m2 = newMovement(acc, "150.00", "CUSTOMER_PAYMENT");
        String p2 = POST("/api/deals/" + deal + "/payments", "{\"movementId\":" + m2 + "}");
        assertThat((String) read(p2, "$.items[1].kind")).isEqualTo("FINAL");
        assertThat((String) read(p2, "$.items[1].amount")).isEqualTo("100.00");
        assertThat((String) read(p2, "$.status")).isEqualTo("PAID");
        String d2 = GET("/api/deals/" + deal);
        assertThat(step(d2, "FINAL_PAYMENT")).containsEntry("done", true);
        assertThat((Boolean) read(d2, "$.awaitingPayment")).isFalse();
        assertThat((List<?>) read(GET("/api/dashboard"), "$.awaitingPayments[?(@.dealId==" + deal + ")]")).isEmpty();

        // m2-də 50.00 qalıb: bölüşdürülməmiş siyahıda göründü
        List<Map<String, Object>> un = read(GET("/api/movements/unallocated?direction=IN"), "$");
        assertThat(un.stream().filter(x -> ((Number) x.get("id")).longValue() == m2)).singleElement()
                .satisfies(x -> assertThat(x).containsEntry("remaining", "50.00").containsEntry("allocated", "100.00"));
        assertThat(un.stream().anyMatch(x -> ((Number) x.get("id")).longValue() == m1)).isFalse();
        // hesab qalığı bağlantılardan təsirlənmir
        List<Map<String, Object>> accs = read(GET("/api/accounts"), "$");
        assertThat(accs.stream().filter(x -> ((Number) x.get("id")).longValue() == acc).findFirst().orElseThrow()).containsEntry("balance", "250.00");
    }

    @Test
    void sertlerVeZolaq() throws Exception {
        long cust = newCustomer("Şərt Müştərisi");
        String pre = GET("/api/deals/" + dealWith(cust, "80.00", "PREPAID", null));
        assertThat((String) read(pre, "$.advancePercent")).isEqualTo("100.00");
        assertThat((String) read(pre, "$.advanceRequired")).isEqualTo("80.00");
        assertThat(keys(pre)).containsExactly("NEW", "CONTRACT", "PROTOCOL", "ADVANCE_INVOICE", "PAID", "IN_PROGRESS", "ACT", "INVOICE", "DONE");
        long postId = id(POST("/api/deals", "{\"customerId\":" + cust + ",\"price\":\"80.00\",\"computers\":1}"));
        String post = GET("/api/deals/" + postId);
        assertThat((String) read(post, "$.paymentTerms")).isEqualTo("POSTPAID");
        assertThat((String) read(post, "$.advanceRequired")).isEqualTo("0.00");
        assertThat(keys(post)).containsExactly("NEW", "CONTRACT", "PROTOCOL", "IN_PROGRESS", "ACT", "INVOICE", "PAID", "DONE");
        assertThat(step(post, "PAID")).containsEntry("label", "Ödəniş").containsEntry("done", false);
        // şərtin dəyişməsi
        String u = PUT("/api/deals/" + postId, "{\"paymentTerms\":\"PARTIAL\",\"advancePercent\":\"30\"}");
        assertThat((String) read(u, "$.advanceRequired")).isEqualTo("24.00");
    }

    @Test
    void birHereketIkiSatisaVeArtiqBolusdurme() throws Exception {
        long acc = newAccount("Ödəniş bank 2", "BANK", "0.00");
        long cust = newCustomer("Bölünən Müştəri");
        long dA = newDeal(cust, "60.00"), dB = newDeal(cust, "100.00");
        long m = newMovement(acc, "100.00", "CUSTOMER_PAYMENT");   // satışa yalnız müştəri ödənişi bağlanır
        POST("/api/deals/" + dA + "/payments", "{\"movementId\":" + m + ",\"amount\":\"60.00\",\"kind\":\"FINAL\"}");
        POST("/api/deals/" + dB + "/payments", "{\"movementId\":" + m + ",\"amount\":\"30.00\",\"kind\":\"OTHER\",\"note\":\"hissə\"}");
        // artıq bölüşdürmə: boş qalıq 10.00
        failPost("/api/deals/" + dB + "/payments", "{\"movementId\":" + m + ",\"amount\":\"10.01\"}", 409);
        POST("/api/deals/" + dB + "/payments", "{\"movementId\":" + m + ",\"amount\":\"10.00\"}");
        failPost("/api/deals/" + dB + "/payments", "{\"movementId\":" + m + ",\"amount\":\"0.01\"}", 409);
        failPost("/api/deals/" + dB + "/payments", "{\"movementId\":" + m + "}", 400);   // qalıq yoxdur
        List<Map<String, Object>> al = read(GET("/api/movements/" + m + "/allocations"), "$");
        assertThat(al).hasSize(3);
        assertThat(al.get(0)).containsEntry("customerName", "Bölünən Müştəri").containsKey("contractNo");
        assertThat((String) read(GET("/api/deals/" + dB + "/payments"), "$.paid")).isEqualTo("40.00");
        assertThat((String) read(GET("/api/deals/" + dA + "/payments"), "$.status")).isEqualTo("PAID");
        List<Map<String, Object>> mv = read(GET("/api/movements?accountId=" + acc), "$");
        assertThat(mv.get(0)).containsEntry("allocated", "100.00");
        assertThat((List<?>) mv.get(0).get("allocations")).hasSize(3);
        // OUT hərəkəti bağlanmır, olmayan hərəkət 404
        long out = id(POST("/api/movements", "{\"date\":\"2026-06-02\",\"accountId\":" + acc + ",\"direction\":\"OUT\",\"amount\":\"5.00\",\"purpose\":\"OTHER\"}"));
        failPost("/api/deals/" + dA + "/payments", "{\"movementId\":" + out + ",\"amount\":\"1.00\"}", 400);
        assertThat(run(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/deals/" + dA + "/payments"), "{\"movementId\":99999999}").getResponse().getStatus()).isEqualTo(404);
        // OUT bağlantı ilə yaradılmır
        failPost("/api/movements", "{\"date\":\"2026-06-02\",\"accountId\":" + acc + ",\"direction\":\"OUT\",\"amount\":\"5.00\",\"purpose\":\"OTHER\",\"allocation\":{\"dealId\":" + dA + "}}", 400);
    }

    @Test
    void ayirVeYenidenBagla() throws Exception {
        long acc = newAccount("Ödəniş bank 3", "BANK", "0.00");
        long cust = newCustomer("Yenidən Müştəri");
        long dA = newDeal(cust, "100.00"), dB = newDeal(cust, "100.00");
        long m = newMovement(acc, "100.00", "CUSTOMER_PAYMENT");
        String p = POST("/api/deals/" + dA + "/payments", "{\"movementId\":" + m + ",\"note\":\"səhv satış\"}");
        long allocId = ((Number) read(p, "$.items[0].id")).longValue();
        // ayır: hərəkət qalır, satış ödənilməmiş olur
        DELETE("/api/payment-allocations/" + allocId);
        assertThat((String) read(GET("/api/deals/" + dA + "/payments"), "$.status")).isEqualTo("UNPAID");
        assertThat((List<?>) read(GET("/api/movements?accountId=" + acc), "$")).hasSize(1);
        assertThat((List<?>) read(GET("/api/movements/unallocated?direction=IN"), "$[?(@.id==" + m + ")]")).hasSize(1);
        // başqa satışa bağla, sonra PUT ilə üçüncüyə keçir və məbləği dəyiş
        String p2 = POST("/api/deals/" + dB + "/payments", "{\"movementId\":" + m + "}");
        long alloc2 = ((Number) read(p2, "$.items[0].id")).longValue();
        long dC = newDeal(cust, "40.00");
        String moved = PUT("/api/payment-allocations/" + alloc2, "{\"dealId\":" + dC + ",\"amount\":\"40.00\",\"kind\":\"FINAL\",\"note\":\"düzəldildi\"}");
        assertThat((String) read(moved, "$.status")).isEqualTo("PAID");
        assertThat((String) read(GET("/api/deals/" + dB + "/payments"), "$.paid")).isEqualTo("0.00");
        assertThat((String) read(GET("/api/deals/" + dC + "/payments"), "$.items[0].note")).isEqualTo("düzəldildi");
        // dəyişdirmə də hərəkətin qalığını aşa bilməz
        failPut("/api/payment-allocations/" + alloc2, "{\"amount\":\"100.01\"}", 409);
        failDelete("/api/payment-allocations/99999999", 404);
    }

    private void failPut(String path, String json, int status) throws Exception {
        fail(run(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path), json), status);
    }

    @Test
    void baglıHereketSilinmirVeKicilmir() throws Exception {
        long acc = newAccount("Ödəniş bank 4", "BANK", "0.00");
        long cust = newCustomer("Bağlı Müştəri");
        long deal = newDeal(cust, "100.00");
        // yeni mədaxil yaz və bağla (satışdan)
        String mv = POST("/api/movements", "{\"date\":\"2026-06-03\",\"accountId\":" + acc + ",\"direction\":\"IN\",\"amount\":\"70.00\",\"purpose\":\"CUSTOMER_PAYMENT\","
                + "\"allocation\":{\"dealId\":" + deal + ",\"kind\":\"OTHER\"}}");
        long m = id(mv);
        assertThat((String) read(mv, "$.allocated")).isEqualTo("70.00");
        assertThat((String) read(GET("/api/deals/" + deal + "/payments"), "$.paid")).isEqualTo("70.00");
        failDelete("/api/movements/" + m, 409);
        String body = "{\"date\":\"2026-06-03\",\"accountId\":" + acc + ",\"direction\":\"IN\",\"amount\":\"%s\",\"purpose\":\"CUSTOMER_PAYMENT\"}";
        failPut("/api/movements/" + m, String.format(body, "69.99"), 409);
        failPut("/api/movements/" + m, String.format(body, "70.00").replace("\"IN\"", "\"OUT\""), 409);
        PUT("/api/movements/" + m, String.format(body, "90.00"));   // artırmaq olar
        // bağlantısı olan satış silinmir (409); ayırandan sonra silinir, hərəkət qalır
        long deal2 = newDeal(cust, "10.00");
        long m2 = id(POST("/api/movements", "{\"date\":\"2026-06-03\",\"accountId\":" + acc + ",\"direction\":\"IN\",\"amount\":\"10.00\",\"purpose\":\"CUSTOMER_PAYMENT\",\"allocation\":{\"dealId\":" + deal2 + "}}"));
        failDelete("/api/deals/" + deal2, 409);
        long a2 = ((Number) read(GET("/api/deals/" + deal2 + "/payments"), "$.items[0].id")).longValue();
        DELETE("/api/payment-allocations/" + a2);
        DELETE("/api/deals/" + deal2);
        DELETE("/api/movements/" + m2);
    }

    /** Köhnə məlumatın köçürülməsi: V12 skriptinin insert sətirləri real H2 bazasında işlədilir */
    @Test
    void kohneOdenisKochurulur() throws Exception {
        long acc = newAccount("Köhnə bank", "BANK", "0.00");
        long cust = newCustomer("Köhnə Müştəri");
        E.Movement m1 = legacyMovement(acc, "100.00", null);
        E.Deal d1 = legacyDeal(cust, "100.00", m1.id, null, null);          // avanssız, sonradan ödəniş -> FINAL
        E.Movement m2 = legacyMovement(acc, "60.00", null);
        E.Deal d2 = legacyDeal(cust, "200.00", m2.id, "INX-AV-1", new BigDecimal("50.00"));   // avans qaiməsi var -> PREPAID -> ADVANCE, payment_amount 50
        E.Movement m3 = legacyMovement(acc, "30.00", null);
        E.Deal d3 = legacyDeal(cust, "100.00", null, null, null);
        m3.dealId = d3.id;
        movementRepo.save(m3);   // yalnız movement.deal_id
        // backfill: advance_invoice_no olan satış PREPAID
        jdbc.update("update deal set payment_terms='PREPAID', advance_percent=100 where advance_invoice_no is not null and id = ?", d2.id);

        String sql = new String(getClass().getResourceAsStream("/db/migration/V12__odenis_sertleri_ve_baglantilari.sql").readAllBytes(), StandardCharsets.UTF_8);
        for (String stmt : sql.split(";")) {
            String s = stmt.replaceAll("(?m)^--.*$", "").trim();
            if (s.startsWith("insert into payment_allocation")) jdbc.update(s);
        }

        String p1 = GET("/api/deals/" + d1.id + "/payments");
        assertThat((String) read(p1, "$.paid")).isEqualTo("100.00");
        assertThat((String) read(p1, "$.items[0].kind")).isEqualTo("FINAL");
        assertThat(((Number) read(p1, "$.items[0].movementId")).longValue()).isEqualTo(m1.id);
        String p2 = GET("/api/deals/" + d2.id + "/payments");
        assertThat((String) read(p2, "$.paid")).isEqualTo("50.00");   // payment_amount
        assertThat((String) read(p2, "$.items[0].kind")).isEqualTo("ADVANCE");
        assertThat((String) read(p2, "$.advancePaid")).isEqualTo("50.00");
        assertThat((String) read(GET("/api/deals/" + d3.id + "/payments"), "$.paid")).isEqualTo("30.00");
        // köhnə hərəkət artıq bağlıdır; hesab qalığı dəyişmir
        assertThat((List<?>) read(GET("/api/movements/unallocated"), "$[?(@.id==" + m1.id + ")]")).isEmpty();
        assertThat((List<?>) read(GET("/api/movements/unallocated"), "$[?(@.id==" + m2.id + ")]")).hasSize(1);   // 60-50 = 10 boş
        failDelete("/api/movements/" + m1.id, 409);
    }

    private E.Movement legacyMovement(long acc, String amount, Long dealId) {
        E.Movement m = new E.Movement();
        m.entryDate = LocalDate.of(2026, 5, 5);
        m.accountId = acc;
        m.direction = "IN";
        m.amount = new BigDecimal(amount);
        m.purpose = "CUSTOMER_PAYMENT";
        m.dealId = dealId;
        return movementRepo.save(m);
    }

    private E.Deal legacyDeal(long cust, String price, Long movementId, String advNo, BigDecimal paymentAmount) {
        E.Deal d = new E.Deal();
        d.customerId = cust;
        d.product = "Köhnə";
        d.price = new BigDecimal(price);
        d.computers = 1;
        d.stage = "PAID";
        d.saleDate = LocalDate.of(2026, 5, 1);
        d.createdAt = LocalDateTime.now();
        d.paymentMovementId = movementId;
        d.paymentAmount = paymentAmount;
        d.advanceInvoiceNo = advNo;
        return dealRepo.save(d);
    }
}
