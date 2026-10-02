package az.innotex.sade;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Satış tarixi və mərhələ tarixi keçmişə (və gələcəyə) qoyula bilər */
class SaleDateTest extends ApiTest {

    @Test
    void satisTarixiKecmisde() throws Exception {
        long cust = newCustomer("Tarix Müştərisi");
        long acc = newAccount("Tarix bank", "BANK", "0.00");
        // tarix verilməyəndə bu gün
        long d0 = newDeal(cust, "10.00");
        assertThat((String) read(GET("/api/deals/" + d0), "$.saleDate")).isEqualTo(LocalDate.now().toString());

        long d1 = id(POST("/api/deals", "{\"customerId\":" + cust + ",\"price\":\"50.00\",\"computers\":1,\"saleDate\":\"2025-01-15\"}"));
        assertThat((String) read(GET("/api/deals/" + d1), "$.saleDate")).isEqualTo("2025-01-15");
        List<Map<String, Object>> ev0 = read(GET("/api/deals/" + d1 + "/events"), "$");
        assertThat(ev0.get(0).get("date")).isEqualTo("2025-01-15");

        // yeniləmə (keçmiş və gələcək)
        assertThat((String) read(PUT("/api/deals/" + d1, "{\"saleDate\":\"2024-03-01\"}"), "$.saleDate")).isEqualTo("2024-03-01");
        assertThat((String) read(PUT("/api/deals/" + d1, "{\"saleDate\":\"2099-01-01\"}"), "$.saleDate")).isEqualTo("2099-01-01");
        PUT("/api/deals/" + d1, "{\"saleDate\":\"2024-03-01\"}");

        // siyahı sale_date desc, id desc
        List<Map<String, Object>> list = read(GET("/api/deals"), "$");
        int i0 = -1, i1 = -1;
        for (int i = 0; i < list.size(); i++) {
            long id = ((Number) list.get(i).get("id")).longValue();
            if (id == d0) i0 = i;
            if (id == d1) i1 = i;
        }
        assertThat(i0).isGreaterThanOrEqualTo(0).isLessThan(i1);

        // mərhələ keçidi keçmiş tarixlə: hadisə tarixi və boş mərhələ tarixi (akt) default olur
        String s = POST("/api/deals/" + d1 + "/stage", "{\"stage\":\"ACT\",\"eventDate\":\"2024-04-10\"}");
        assertThat((String) read(s, "$.actDate")).isEqualTo("2024-04-10");
        List<Map<String, Object>> ev = read(GET("/api/deals/" + d1 + "/events"), "$");
        assertThat(ev.get(0).get("date")).isEqualTo("2024-04-10");
    }
}
