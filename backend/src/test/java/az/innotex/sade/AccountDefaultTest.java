package az.innotex.sade;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Əsas hesab və satışa yalnız "Müştəri ödənişi" mədaxilinin bağlanması */
class AccountDefaultTest extends ApiTest {

    private List<Map<String, Object>> defaults() throws Exception {
        List<Map<String, Object>> all = read(GET("/api/accounts"), "$");
        return all.stream().filter(a -> Boolean.TRUE.equals(a.get("isDefault"))).toList();
    }

    @Test
    void esasHesab() throws Exception {
        long card = newAccount("Əsas test kartı", "DIRECTOR_CARD", "0");
        long bank = newAccount("Əsas test bankı", "BANK", "0");
        // həmişə ən çox bir əsas hesab var
        assertThat(defaults()).hasSizeLessThanOrEqualTo(1);

        String r = POST("/api/accounts/" + bank + "/default", null);
        assertThat((Boolean) read(r, "$.isDefault")).isTrue();
        assertThat(defaults()).hasSize(1);
        assertThat(((Number) defaults().get(0).get("id")).longValue()).isEqualTo(bank);

        POST("/api/accounts/" + card + "/default", null);
        assertThat(defaults()).hasSize(1);
        assertThat(((Number) defaults().get(0).get("id")).longValue()).isEqualTo(card);
        fail(run(post("/api/accounts/999999/default"), null), 404);
    }

    @Test
    void yalnizMusteriOdenisiBaglanir() throws Exception {
        long acc = newAccount("Təyinat test bankı", "BANK", "0");
        long deal = newDeal(newCustomer("Təyinat Müştərisi"), "100.00");
        String mv = "{\"date\":\"2026-07-10\",\"accountId\":" + acc + ",\"direction\":\"IN\",\"amount\":\"100.00\",\"purpose\":\"%s\"}";

        long other = id(POST("/api/movements", mv.formatted("OTHER")));
        failPost("/api/deals/" + deal + "/payments", "{\"movementId\":" + other + "}", 400);
        List<Map<String, Object>> un = read(GET("/api/movements/unallocated?direction=IN"), "$");
        assertThat(un.stream().map(x -> ((Number) x.get("id")).longValue())).doesNotContain(other);

        long pay = id(POST("/api/movements", mv.formatted("CUSTOMER_PAYMENT")));
        POST("/api/deals/" + deal + "/payments", "{\"movementId\":" + pay + "}");
        // bağlı hərəkətin təyinatı dəyişdirilə bilməz
        fail(run(put("/api/movements/" + pay), mv.formatted("OTHER")), 409);
    }
}
