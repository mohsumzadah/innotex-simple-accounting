package az.innotex.sade;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Satışın miqdarı, yer tutucuları və bir mərhələdə əlavə sənəd */
class DealQuantityTest extends ApiTest {

    @Test
    void miqdarVeYerTutucular() throws Exception {
        long c = newCustomer("Miqdar Müştərisi");
        // default miqdar 1
        long d1 = newDeal(c, "100.00");
        assertThat((Integer) read(GET("/api/deals/" + d1), "$.quantity")).isEqualTo(1);

        String j = POST("/api/deals", "{\"customerId\":" + c + ",\"product\":\"Lisenziya\",\"price\":\"300.00\",\"computers\":3,\"quantity\":3}");
        long d = id(j);
        assertThat((Integer) read(j, "$.quantity")).isEqualTo(3);

        // dəyişmə və yanlış dəyər
        assertThat((Integer) read(PUT("/api/deals/" + d, "{\"quantity\":4,\"price\":\"400.00\"}"), "$.quantity")).isEqualTo(4);
        fail(run(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/deals/" + d), "{\"quantity\":0}"), 400);
        failPost("/api/deals", "{\"customerId\":" + c + ",\"price\":\"1\",\"quantity\":0}", 400);
        // quantity göndərilməyəndə dəyişmir
        assertThat((Integer) read(PUT("/api/deals/" + d, "{\"note\":\"x\"}"), "$.quantity")).isEqualTo(4);
        // qiymət dəyişəndə ödəniş kartı yeni qiyməti göstərir
        assertThat((String) read(GET("/api/deals/" + d + "/payments"), "$.price")).isEqualTo("400.00");

        // şablonda {{deal.quantity}} və {{deal.unitPrice}}
        long tpl = id(POST("/api/templates", "{\"code\":\"PROTOCOL\",\"title\":\"Miqdar testi\",\"html\":\"<p>{{deal.quantity}} x {{deal.unitPrice}} = {{deal.price}}</p>\",\"isDefault\":false}"));
        String doc = POST("/api/deals/" + d + "/documents", "{\"code\":\"PROTOCOL\",\"templateId\":" + tpl + "}");
        assertThat((String) read(doc, "$.html")).contains("4 x 100.00 = 400.00");

        // eyni mərhələdə ikinci (əlavə) sənəd
        POST("/api/deals/" + d + "/documents", "{\"code\":\"PROTOCOL\",\"templateId\":" + tpl + "}");
        List<Map<String, Object>> list = read(GET("/api/deals/" + d + "/documents"), "$");
        assertThat(list.stream().filter(x -> "PROTOCOL".equals(x.get("code")))).hasSize(2);
    }
}
