package az.innotex.sade;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SalesLedgerTest extends ApiTest {

    private Map<String, Object> account(long id) throws Exception {
        List<Map<String, Object>> l = read(GET("/api/accounts"), "$");
        return l.stream().filter(a -> ((Number) a.get("id")).longValue() == id).findFirst().orElseThrow();
    }

    @Test
    void satisMerhelelerVeOdenis() throws Exception {
        long acc = newAccount("Test bank", "BANK", "0.00");
        long cust = newCustomer("Test Müştəri A");
        long deal = newDeal(cust, "100.00");
        String d0 = GET("/api/deals/" + deal);
        assertThat((String) read(d0, "$.stage")).isEqualTo("NEW");
        assertThat((String) read(d0, "$.product")).isEqualTo("INNOTEX e-Qaimə");
        assertThat((String) read(d0, "$.price")).isEqualTo("100.00");

        // müqavilə: boş nömrə -> avtomatik INX-YYYY-PS-NNN, boş tarix sətri null sayılır
        String d1 = POST("/api/deals/" + deal + "/stage", "{\"stage\":\"CONTRACT\",\"contractNo\":\"\",\"contractDate\":\"\",\"note\":\"imzalandı\"}");
        assertThat((String) read(d1, "$.contractNo")).matches("INX-\\d{4}-PS-\\d{3}");
        assertThat((String) read(d1, "$.contractDate")).isNotBlank();

        // ikinci satışın nömrəsi ardıcıl artır
        long deal2 = newDeal(cust, "100.00");
        String c2 = read(POST("/api/deals/" + deal2 + "/stage", "{\"stage\":\"CONTRACT\"}"), "$.contractNo");
        String c1 = read(d1, "$.contractNo");
        assertThat(Integer.parseInt(c2.substring(c2.length() - 3))).isEqualTo(Integer.parseInt(c1.substring(c1.length() - 3)) + 1);

        // PAID mərhələsi göstəricidir: hərəkət yaratmır (köhnə sahələr və hesab nəzərə alınmır)
        String d2 = POST("/api/deals/" + deal + "/stage", "{\"stage\":\"PAID\",\"paymentDate\":\"2026-05-05\",\"paymentAmount\":\"100.00\",\"accountId\":" + acc + "}");
        assertThat((String) read(d2, "$.stage")).isEqualTo("PAID");
        assertThat((Object) read(d2, "$.paymentMovementId")).isNull();
        assertThat((List<?>) read(GET("/api/movements?accountId=" + acc), "$")).isEmpty();
        assertThat(account(acc)).containsEntry("balance", "0.00");

        // bank mədaxili yazılır və satışa bağlanır
        String mv = POST("/api/movements", "{\"date\":\"2026-05-05\",\"accountId\":" + acc + ",\"direction\":\"IN\",\"amount\":\"100.00\",\"purpose\":\"CUSTOMER_PAYMENT\"}");
        long movId = id(mv);
        assertThat(account(acc)).containsEntry("balance", "100.00");
        String pay = POST("/api/deals/" + deal + "/payments", "{\"movementId\":" + movId + "}");
        assertThat((String) read(pay, "$.paid")).isEqualTo("100.00");
        assertThat((String) read(pay, "$.status")).isEqualTo("PAID");
        assertThat(account(acc)).containsEntry("balance", "100.00");

        // mərhələlər istənilən ardıcıllıqla (geri də), tarixçə saxlanılır
        POST("/api/deals/" + deal + "/stage", "{\"stage\":\"IN_PROGRESS\"}");
        POST("/api/deals/" + deal + "/stage", "{\"stage\":\"ACT\",\"actNo\":\"01\",\"actDate\":\"2026-05-20\"}");
        POST("/api/deals/" + deal + "/stage", "{\"stage\":\"CONTRACT\"}");
        List<Map<String, Object>> ev = read(GET("/api/deals/" + deal + "/events"), "$");
        assertThat(ev.get(0)).containsEntry("toStage", "CONTRACT").containsEntry("fromStage", "ACT");
        assertThat(ev).hasSizeGreaterThanOrEqualTo(5);
        assertThat(ev.get(0)).containsKey("createdAt");

        // yanlış mərhələ, yalnız NEW silinir
        failPost("/api/deals/" + deal + "/stage", "{\"stage\":\"XYZ\"}", 400);
        failDelete("/api/deals/" + deal, 409);
        DELETE("/api/deals/" + newDeal(cust, "50.00"));

        // bağlı hərəkət silinmir; ayrıldıqdan sonra silinir
        failDelete("/api/movements/" + movId, 409);
        long allocId = ((Number) read(GET("/api/deals/" + deal + "/payments"), "$.items[0].id")).longValue();
        DELETE("/api/payment-allocations/" + allocId);
        DELETE("/api/movements/" + movId);

        // müştəri satışı olduğu üçün silinmir
        failDelete("/api/customers/" + cust, 409);
        // başqa şirkətin/olmayan id
        assertThat(run(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/deals/99999999"), null).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void xercHereketYaradirVeSilir() throws Exception {
        long acc = newAccount("Test kart", "DIRECTOR_CARD", "0.00");
        String e = POST("/api/expenses", "{\"date\":\"2026-06-10\",\"vendor\":\"Test Hosting\",\"category\":\"Server/Hosting\",\"description\":\"aylıq\","
                + "\"amount\":\"50.00\",\"currency\":\"USD\",\"rate\":\"1.70\",\"amountAzn\":\"\",\"accountId\":" + acc + ",\"deductible\":true,\"fileId\":null,\"note\":\"\"}");
        long expId = id(e);
        assertThat((String) read(e, "$.amountAzn")).isEqualTo("85.00");

        List<Map<String, Object>> movs = read(GET("/api/movements?accountId=" + acc), "$");
        assertThat(movs).hasSize(1);
        assertThat(movs.get(0)).containsEntry("direction", "OUT").containsEntry("purpose", "EXPENSE").containsEntry("amount", "85.00");
        assertThat(((Number) movs.get(0).get("expenseId")).longValue()).isEqualTo(expId);
        assertThat(account(acc)).containsEntry("balance", "-85.00");

        // xərci dəyişəndə hərəkət də dəyişir; bankın real tutduğu məbləğ
        PUT("/api/expenses/" + expId, "{\"date\":\"2026-06-10\",\"vendor\":\"Test Hosting\",\"category\":\"Server/Hosting\",\"amount\":\"50.00\","
                + "\"currency\":\"USD\",\"rate\":\"1.70\",\"amountAzn\":\"86.10\",\"accountId\":" + acc + ",\"deductible\":true}");
        assertThat(account(acc)).containsEntry("balance", "-86.10");

        // xərc hərəkətini birbaşa silmək olmaz; hesab istifadədədir
        long movId = ((Number) movs.get(0).get("id")).longValue();
        failDelete("/api/movements/" + movId, 409);
        failDelete("/api/accounts/" + acc, 409);

        DELETE("/api/expenses/" + expId);
        assertThat((List<?>) read(GET("/api/movements?accountId=" + acc), "$")).isEmpty();
        assertThat(account(acc)).containsEntry("balance", "0.00");
        DELETE("/api/accounts/" + acc);
    }

    @Test
    void direktoraBorc() throws Exception {
        long card = newAccount("Test şəxsi kart", "DIRECTOR_CARD", "0.00");
        long bank = newAccount("Test bank 2", "BANK", "1000.00");
        String before = read(GET("/api/dashboard"), "$.ownerDebt");
        POST("/api/expenses", "{\"date\":\"2026-06-11\",\"category\":\"Digər\",\"amount\":\"200.00\",\"accountId\":" + card + "}");
        assertThat(new java.math.BigDecimal((String) read(GET("/api/dashboard"), "$.ownerDebt")).subtract(new java.math.BigDecimal(before)))
                .isEqualByComparingTo("200.00");
        POST("/api/movements", "{\"date\":\"2026-06-12\",\"accountId\":" + bank + ",\"direction\":\"OUT\",\"amount\":\"50.00\",\"purpose\":\"OWNER_REPAYMENT\"}");
        assertThat(new java.math.BigDecimal((String) read(GET("/api/dashboard"), "$.ownerDebt")).subtract(new java.math.BigDecimal(before)))
                .isEqualByComparingTo("150.00");
    }

    @Test
    void kontragentRekvizitleri() throws Exception {
        String c = POST("/api/counterparties", "{\"name\":\"Test Kontragent MMC\",\"voen\":\"0000000002\",\"iban\":\"AZ00TEST00000000000000000000\","
                + "\"bank\":\"Test Bank\",\"bankCode\":\"000000\",\"bankVoen\":\"0000000003\",\"swift\":\"TESTAZ22\",\"correspondentAccount\":\"AZ00CORR\",\"note\":\"sınaq\"}");
        assertThat((String) read(c, "$.kind")).isEqualTo("CUSTOMER");
        assertThat((String) read(c, "$.bankVoen")).isEqualTo("0000000003");
        assertThat((String) read(c, "$.correspondentAccount")).isEqualTo("AZ00CORR");
        long id = id(c);
        String u = PUT("/api/customers/" + id, "{\"name\":\"Test Kontragent MMC\",\"kind\":\"supplier\"}");
        assertThat((String) read(u, "$.kind")).isEqualTo("SUPPLIER");
        failPost("/api/counterparties", "{\"name\":\"X\",\"kind\":\"BAD\"}", 400);
        DELETE("/api/counterparties/" + id);
    }
}
