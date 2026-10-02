package az.innotex.sade;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductsTest extends ApiTest {

    private String prod(String name, String code) {
        return "{\"name\":\"" + name + "\",\"code\":\"" + code + "\",\"defaultPrice\":\"250\",\"defaultComputers\":3,\"description\":\"Test təsvir\",\"active\":true}";
    }

    private void invoice(long deal, String date, String amount) throws Exception {
        POST("/api/deals/" + deal + "/stage", "{\"stage\":\"INVOICE\",\"invoiceNo\":\"P-1\",\"invoiceDate\":\"" + date + "\",\"invoiceAmount\":\"" + amount + "\"}");
    }

    @Test
    void crudVe409() throws Exception {
        String created = POST("/api/products", prod("Test məhsul A", "TM-001"));
        long id = id(created);
        assertThat((String) read(created, "$.unit")).isEqualTo("ədəd");
        assertThat((String) read(created, "$.defaultPrice")).isEqualTo("250.00");
        assertThat(((Number) read(created, "$.defaultComputers")).intValue()).isEqualTo(3);
        failPost("/api/products", prod("test məhsul a", "X"), 409);

        String upd = PUT("/api/products/" + id, prod("Test məhsul A2", "TM-002").replace("\"active\":true", "\"active\":false"));
        assertThat((String) read(upd, "$.code")).isEqualTo("TM-002");
        List<Map<String, Object>> act = read(GET("/api/products?active=true"), "$");
        assertThat(act.stream().anyMatch(m -> ((Number) m.get("id")).longValue() == id)).isFalse();
        List<Map<String, Object>> pas = read(GET("/api/products?active=false"), "$");
        assertThat(pas.stream().anyMatch(m -> ((Number) m.get("id")).longValue() == id)).isTrue();

        // satışda istifadə olunan məhsul silinmir
        long cust = newCustomer("Məhsul Müştərisi");
        POST("/api/deals", "{\"customerId\":" + cust + ",\"productId\":" + id + ",\"price\":\"10\",\"computers\":1}");
        failDelete("/api/products/" + id, 409);

        long free = id(POST("/api/products", prod("Test silinən", "TS-1")));
        DELETE("/api/products/" + free);
        failDelete("/api/products/" + free, 404);
    }

    @Test
    void satisMehsuluKopyalayirVeSablonDolur() throws Exception {
        long pid = id(POST("/api/products", prod("Test Xidmət B", "TX-7")));
        long cust = newCustomer("Şablon Müştərisi");
        String deal = POST("/api/deals", "{\"customerId\":" + cust + ",\"productId\":" + pid + ",\"product\":\"başqa ad\",\"price\":\"100\",\"computers\":1}");
        long dealId = id(deal);
        assertThat((String) read(deal, "$.product")).isEqualTo("Test Xidmət B");
        assertThat(((Number) read(deal, "$.productId")).longValue()).isEqualTo(pid);
        assertThat((String) read(deal, "$.productCode")).isEqualTo("TX-7");

        long tpl = id(POST("/api/templates", "{\"code\":\"ACT\",\"title\":\"Test məhsul şablonu\",\"html\":\"<p>{{product.name}}|{{product.code}}|{{product.unit}}|{{product.description}}</p>\",\"isDefault\":false}"));
        String doc = POST("/api/deals/" + dealId + "/documents", "{\"code\":\"ACT\",\"templateId\":" + tpl + "}");
        assertThat((String) read(doc, "$.html")).contains("<p>Test Xidmət B|TX-7|ədəd|Test təsvir</p>");

        // məhsulsuz satış: kod boş xətt olur, ad satışın mətnidir
        long d2 = newDeal(cust, "50");
        String doc2 = POST("/api/deals/" + d2 + "/documents", "{\"code\":\"ACT\",\"templateId\":" + tpl + "}");
        assertThat((String) read(doc2, "$.html")).contains("<p>INNOTEX e-Qaimə|__________|__________|__________</p>");

        // bağı silmək (0) mətni saxlayır
        String cleared = PUT("/api/deals/" + dealId, "{\"productId\":0}");
        assertThat((Object) read(cleared, "$.productId")).isNull();
        assertThat((String) read(cleared, "$.product")).isEqualTo("Test Xidmət B");
    }

    @Test
    void gelirMehsulUzre() throws Exception {
        long pid = id(POST("/api/products", prod("Test Hesabat Məhsulu", "TH-1")));
        long cust = newCustomer("Məhsul Hesabatı");
        long d1 = id(POST("/api/deals", "{\"customerId\":" + cust + ",\"productId\":" + pid + ",\"price\":\"600\",\"computers\":1}"));
        long d2 = id(POST("/api/deals", "{\"customerId\":" + cust + ",\"productId\":" + pid + ",\"price\":\"400\",\"computers\":1}"));
        long d3 = id(POST("/api/deals", "{\"customerId\":" + cust + ",\"product\":\"Sərbəst mətn\",\"price\":\"300\",\"computers\":1}"));
        invoice(d1, "2031-02-10", "600.00");
        invoice(d2, "2031-03-10", "400.00");
        invoice(d3, "2031-03-11", "300.00");
        String q1 = GET("/api/reports/quarter?year=2031&q=1");
        assertThat((String) read(q1, "$.incomeByProduct[0].product")).isEqualTo("Test Hesabat Məhsulu");
        assertThat((String) read(q1, "$.incomeByProduct[0].amount")).isEqualTo("1000.00");
        assertThat((String) read(q1, "$.incomeByProduct[1].product")).isEqualTo("Sərbəst mətn");
        assertThat((String) read(q1, "$.incomeByProduct[1].amount")).isEqualTo("300.00");
        assertThat((List<?>) read(GET("/api/reports/year?year=2031"), "$.incomeByProduct")).hasSize(2);
        assertThat(run(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/reports/quarter.xlsx?year=2031&q=1"), null).getResponse().getStatus()).isEqualTo(200);
    }
}
