package az.innotex.sade;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Dəyişiklik 7: satış axınının sadələşdirilməsi (startContract, akt nömrəsi, ödəniş təklifləri) */
class SalesFlowTest extends ApiTest {
    private long movement(long acc, String date, String amount, String note) throws Exception {
        return id(POST("/api/movements", "{\"date\":\"" + date + "\",\"accountId\":" + acc + ",\"direction\":\"IN\",\"amount\":\"" + amount
                + "\",\"purpose\":\"CUSTOMER_PAYMENT\",\"note\":\"" + note + "\"}"));
    }

    @Test
    void startContractBirAddimda() throws Exception {
        long cust = newCustomer("Başlanğıc Müştəri");
        String d = POST("/api/deals", "{\"customerId\":" + cust + ",\"price\":\"300.00\",\"computers\":1,\"saleDate\":\"2026-02-10\",\"startContract\":true}");
        assertThat((String) read(d, "$.stage")).isEqualTo("CONTRACT");
        assertThat((String) read(d, "$.contractNo")).matches("INX-2026-PS-[0-9]{3}");
        assertThat((String) read(d, "$.contractDate")).isEqualTo("2026-02-10");
        long id = id(d);
        List<Map<String, Object>> docs = read(GET("/api/deals/" + id + "/documents"), "$");
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0)).containsEntry("code", "CONTRACT");
        List<Map<String, Object>> ev = read(GET("/api/deals/" + id + "/events"), "$");
        assertThat(ev).hasSize(2);
        assertThat((String) read(d, "$.stageSince")).isEqualTo("2026-02-10");

        // startContract verilməyəndə köhnə davranış
        long plain = newDeal(cust, "10.00");
        String p = GET("/api/deals/" + plain);
        assertThat((String) read(p, "$.stage")).isEqualTo("NEW");
        assertThat((Object) read(p, "$.contractNo")).isNull();
        assertThat((List<?>) read(GET("/api/deals/" + plain + "/documents"), "$")).isEmpty();
    }

    @Test
    void aktNomresiAvtomatik() throws Exception {
        long cust = newCustomer("Akt Müştərisi");
        long a = newDeal(cust, "50.00");
        long b = newDeal(cust, "60.00");
        int year = LocalDate.now().getYear();
        String d1 = POST("/api/deals/" + a + "/stage", "{\"stage\":\"ACT\"}");
        String no1 = read(d1, "$.actNo");
        assertThat(no1).matches("AKT-" + year + "-[0-9]{3}");
        assertThat((String) read(d1, "$.actDate")).isEqualTo(LocalDate.now().toString());
        // şablondan akt sənədi yaradılanda boş nömrə/tarix dolur və ardıcıllıq +1
        POST("/api/deals/" + b + "/documents", "{\"code\":\"ACT\"}");
        String no2 = read(GET("/api/deals/" + b), "$.actNo");
        assertThat(no2).matches("AKT-" + year + "-[0-9]{3}");
        assertThat(Integer.parseInt(no2.substring(no2.lastIndexOf('-') + 1))).isEqualTo(Integer.parseInt(no1.substring(no1.lastIndexOf('-') + 1)) + 1);
        assertThat((String) read(GET("/api/deals/" + b), "$.actDate")).isEqualTo(LocalDate.now().toString());
        // əl ilə yazılmış nömrə toxunulmaz qalır
        long c = newDeal(cust, "70.00");
        assertThat((String) read(POST("/api/deals/" + c + "/stage", "{\"stage\":\"ACT\",\"actNo\":\"01\"}"), "$.actNo")).isEqualTo("01");
    }

    @Test
    void odenisTekliflerininSirasi() throws Exception {
        long acc = newAccount("Təklif bank", "BANK", "0.00");
        long cust = id(POST("/api/customers", "{\"name\":\"Təklif Test Şirkəti\",\"voen\":\"7777777771\"}"));
        String d = POST("/api/deals", "{\"customerId\":" + cust + ",\"price\":\"200.00\",\"computers\":1,\"saleDate\":\"2026-03-01\",\"paymentTerms\":\"PARTIAL\",\"advancePercent\":50,\"startContract\":true}");
        long deal = id(d);
        String no = read(d, "$.contractNo");   // INX-2026-PS-NNN
        String sloppy = no.toLowerCase().replace("-", " - ");   // boşluq və tire fərqi

        long byAmount = movement(acc, "2026-08-01", "100.00", "bank mətni");                 // məbləğ = avans qalığı (+30)
        long byNote = movement(acc, "2026-08-01", "35.00", "Ödəniş " + sloppy + " üzrə");    // müqavilə nömrəsi (+50)
        long byName = movement(acc, "2026-08-01", "36.00", "təklif test şirkəti");           // ad (+20)
        long none = movement(acc, "2026-08-01", "37.00", "başqa");

        List<Map<String, Object>> list = read(GET("/api/deals/" + deal + "/payment-suggestions"), "$");
        List<Long> order = list.stream().map(m -> ((Number) m.get("movementId")).longValue())
                .filter(i -> i == byAmount || i == byNote || i == byName || i == none).toList();
        assertThat(order).containsExactly(byNote, byAmount, byName, none);
        Map<String, Object> top = list.stream().filter(m -> ((Number) m.get("movementId")).longValue() == byNote).findFirst().orElseThrow();
        assertThat((Integer) top.get("score")).isEqualTo(50);
        assertThat((List<Object>) top.get("reasons")).containsExactly("müqavilə nömrəsi");
        Map<String, Object> amt = list.stream().filter(m -> ((Number) m.get("movementId")).longValue() == byAmount).findFirst().orElseThrow();
        assertThat((Integer) amt.get("score")).isEqualTo(30);
        assertThat((List<Object>) amt.get("reasons")).containsExactly("məbləğ eynidir");
        // heç nə avtomatik bağlanmır
        assertThat((String) read(GET("/api/deals/" + deal + "/payments"), "$.paid")).isEqualTo("0.00");
    }
}
