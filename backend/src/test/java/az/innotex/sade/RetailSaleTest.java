package az.innotex.sade;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/** Fiziki şəxslərə satış (Dəyişiklik 9): INDIVIDUAL kontragent, RETAIL şərti, sürətli satış, DONE tarixi ilə gəlir (2033-cü il) */
class RetailSaleTest extends ApiTest {

    private String quick(String json, MockMultipartFile file, int status) throws Exception {
        var b = multipart("/api/deals/quick-retail").file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        if (file != null) b.file(file);
        var r = mvc.perform(b.header("Authorization", "Bearer " + token())).andReturn();
        if (r.getResponse().getStatus() != status) throw new AssertionError("HTTP " + r.getResponse().getStatus() + ": " + r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        return r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static MockMultipartFile receipt() { return new MockMultipartFile("file", "cek.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3}); }

    private long individual(String name) throws Exception {
        return id(POST("/api/customers", "{\"name\":\"" + name + "\",\"kind\":\"INDIVIDUAL\",\"fin\":\"ABC1234\",\"phone\":\"050 000 00 00\",\"iban\":\"AZ00X\"}"));
    }

    @Test
    void fizikiSexsVoenSizYaranir() throws Exception {
        String c = POST("/api/customers", "{\"name\":\"Test Alıcı\",\"kind\":\"INDIVIDUAL\",\"fin\":\"ABC1234\",\"email\":\"a@b.az\",\"iban\":\"AZ00X\"}");
        assertThat((String) read(c, "$.kind")).isEqualTo("CUSTOMER");   // köhnə kind=INDIVIDUAL çevrilir
        assertThat((String) read(c, "$.entityType")).isEqualTo("INDIVIDUAL");
        assertThat((Object) read(c, "$.voen")).isNull();
        assertThat((String) read(c, "$.fin")).isEqualTo("ABC1234");
        assertThat((String) read(c, "$.iban")).isEqualTo("AZ00X");   // IBAN fiziki şəxsdə qalır
        failPost("/api/customers", "{\"name\":\"X\",\"kind\":\"BAD\"}", 400);
        String c2 = POST("/api/customers", "{\"name\":\"Kart Sahibi\",\"entityType\":\"INDIVIDUAL\",\"cardNumber\":\"4169 0000 0000 0000\",\"swift\":\"X\",\"director\":\"Y\"}");
        assertThat((String) read(c2, "$.cardNumber")).isEqualTo("4169 0000 0000 0000");
        assertThat((Object) read(c2, "$.swift")).isNull();
        assertThat((Object) read(c2, "$.director")).isNull();
        failPost("/api/customers", "{\"name\":\"X\",\"entityType\":\"BAD\"}", 400);
        String leg = POST("/api/customers", "{\"name\":\"Hüquqi MMC\",\"fin\":\"ABC1234\",\"cardNumber\":\"1\"}");
        assertThat((String) read(leg, "$.entityType")).isEqualTo("LEGAL");
        assertThat((Object) read(leg, "$.fin")).isNull();
        failPost("/api/customers", "{\"name\":\" \",\"entityType\":\"LEGAL\"}", 400);
    }

    @org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** V18 migrasiyasının köçürmə SQL-i: kind=INDIVIDUAL → entity_type=INDIVIDUAL, kind=CUSTOMER; qalanlar LEGAL */
    @Test
    void migrasiyaKohneKindiKocurur() {
        jdbc.update("insert into customer(name, kind) values ('Köhnə Fiziki','INDIVIDUAL'), ('Köhnə Təchizatçı','SUPPLIER')");
        jdbc.update("update customer set entity_type = 'INDIVIDUAL', kind = 'CUSTOMER' where kind = 'INDIVIDUAL' and entity_type = 'LEGAL'");
        assertThat(jdbc.queryForObject("select entity_type || '/' || kind from customer where name='Köhnə Fiziki'", String.class)).isEqualTo("INDIVIDUAL/CUSTOMER");
        assertThat(jdbc.queryForObject("select entity_type || '/' || kind from customer where name='Köhnə Təchizatçı'", String.class)).isEqualTo("LEGAL/SUPPLIER");
    }

    @Test
    void retailAxiniContractSiz() throws Exception {
        long cust = individual("Axın Alıcısı");
        long d = id(POST("/api/deals", "{\"customerId\":" + cust + ",\"price\":\"100.00\",\"paymentTerms\":\"RETAIL\",\"startContract\":true,\"saleDate\":\"2033-02-01\"}"));
        String deal = GET("/api/deals/" + d);
        assertThat((String) read(deal, "$.paymentTerms")).isEqualTo("RETAIL");
        assertThat((String) read(deal, "$.stage")).isEqualTo("NEW");
        assertThat((Object) read(deal, "$.contractNo")).isNull();
        assertThat(this.<List<String>>read(deal, "$.stepper[*].key")).containsExactly("NEW", "PAID", "IN_PROGRESS", "DONE");
        assertThat((List<?>) read(GET("/api/deals/" + d + "/documents"), "$")).isEmpty();
        failPost("/api/deals/" + d + "/stage", "{\"stage\":\"CONTRACT\"}", 400);
        failPost("/api/deals/" + d + "/documents", "{\"code\":\"CONTRACT\"}", 400);

        assertThat((String) read(POST("/api/deals/" + d + "/complete", "{}"), "$.stage")).isEqualTo("PAID");
        assertThat((String) read(POST("/api/deals/" + d + "/complete", "{}"), "$.stage")).isEqualTo("IN_PROGRESS");
        assertThat((String) read(POST("/api/deals/" + d + "/skip", "{}"), "$.stage")).isEqualTo("DONE");
    }

    @Test
    void sureteliSatisYeniMedaxilFaylIleGelirDoneTarixiIle() throws Exception {
        long acc = newAccount("Retail bankı", "BANK", "0.00");
        String body = "{\"buyer\":{\"name\":\"Yeni Alıcı\",\"phone\":\"051\",\"email\":\"y@a.az\",\"fin\":\"FIN9999\"},\"product\":\"INNOTEX e-Qaimə\",\"price\":\"250.00\","
                + "\"saleDate\":\"2033-04-10\",\"movement\":{\"accountId\":" + acc + ",\"date\":\"2033-04-12\",\"amount\":\"250.00\",\"note\":\"Alici odenisi\"},\"installed\":true}";
        String deal = quick(body, receipt(), 201);
        long d = id(deal);
        assertThat((String) read(deal, "$.stage")).isEqualTo("DONE");
        assertThat((String) read(deal, "$.paymentTerms")).isEqualTo("RETAIL");
        assertThat((String) read(deal, "$.paymentStatus")).isEqualTo("PAID");
        assertThat((Object) read(deal, "$.contractNo")).isNull();
        String pay = GET("/api/deals/" + d + "/payments");
        assertThat((List<?>) read(pay, "$.items")).hasSize(1);
        assertThat((String) read(pay, "$.items[0].kind")).isEqualTo("FINAL");
        long fid = ((Number) read(pay, "$.items[0].fileId")).longValue();
        assertThat(fid).isPositive();
        assertThat((List<?>) read(GET("/api/customers"), "$[?(@.name=='Yeni Alıcı' && @.entityType=='INDIVIDUAL' && @.fin=='FIN9999')]")).hasSize(1);
        assertThat(this.<List<String>>read(GET("/api/deals/" + d + "/events"), "$[*].toStage")).containsExactlyInAnyOrder("NEW", "PAID", "DONE");
        // gəlir DONE tarixi (12.04.2033, Q2) ilə
        String q2 = GET("/api/reports/quarter?year=2033&q=2");
        assertThat((String) read(q2, "$.income")).isEqualTo("250.00");
        assertThat((String) read(q2, "$.incomeItems[0].kind")).isEqualTo("RETAIL");
        assertThat((Object) read(q2, "$.incomeItems[0].invoiceNo")).isNull();
        assertThat((String) read(GET("/api/reports/quarter?year=2033&q=1"), "$.income")).isEqualTo("0.00");
        assertThat(this.<List<String>>read(GET("/api/reports/year?year=2033"), "$.quarters[?(@.q==2)].income")).containsExactly("250.00");

        // yalnız şəkil/PDF
        quick(body, new MockMultipartFile("file", "x.exe", "application/octet-stream", new byte[]{1}), 400);

        // ləğv olunan gəlirdən çıxır
        POST("/api/deals/" + d + "/cancel", "{}");
        assertThat((String) read(GET("/api/reports/quarter?year=2033&q=2"), "$.income")).isEqualTo("0.00");
    }

    @Test
    void sureteliSatisMovcudMedaxilVeTamamlanmamis() throws Exception {
        long acc = newAccount("Retail bankı 2", "BANK", "0.00");
        long cust = individual("Mövcud Alıcı");
        long mov = id(POST("/api/movements", "{\"date\":\"2033-07-05\",\"accountId\":" + acc + ",\"direction\":\"IN\",\"amount\":\"180.00\",\"purpose\":\"CUSTOMER_PAYMENT\",\"note\":\"Mövcud Alıcı\"}"));
        String s = GET("/api/payment-suggestions?amount=180.00&date=2033-07-05&name=M%C3%B6vcud%20Al%C4%B1c%C4%B1");
        assertThat(this.<List<Integer>>read(s, "$[?(@.movementId==" + mov + ")].score")).first().isEqualTo(40);

        String body = "{\"customerId\":" + cust + ",\"price\":\"180.00\",\"saleDate\":\"2033-07-06\",\"movementId\":" + mov + ",\"installed\":false}";
        String deal = quick(body, null, 201);
        long d = id(deal);
        assertThat((String) read(deal, "$.stage")).isEqualTo("PAID");
        assertThat((String) read(deal, "$.paid")).isEqualTo("180.00");
        // quraşdırılmayıb: gəlir yoxdur
        assertThat((String) read(GET("/api/reports/quarter?year=2033&q=3"), "$.income")).isEqualTo("0.00");

        // quraşdırma tamamlanır → gəlir həmin gün
        POST("/api/deals/" + d + "/complete", "{\"eventDate\":\"2033-08-20\"}");
        assertThat((String) read(POST("/api/deals/" + d + "/complete", "{\"eventDate\":\"2033-09-02\"}"), "$.stage")).isEqualTo("DONE");
        assertThat((String) read(GET("/api/reports/quarter?year=2033&q=3"), "$.income")).isEqualTo("180.00");

        // ödənişsiz sürətli satış olmaz
        quick("{\"customerId\":" + cust + ",\"price\":\"10.00\"}", null, 400);
    }

    @Test
    void tamamlanmisAmaOdenilmeyibXeberdarliq() throws Exception {
        long cust = individual("Borclu Alıcı");
        long d = id(POST("/api/deals", "{\"customerId\":" + cust + ",\"price\":\"90.00\",\"paymentTerms\":\"RETAIL\",\"saleDate\":\"2033-10-03\"}"));
        // tamamlanmamış RETAIL satış gəlir deyil
        assertThat((String) read(GET("/api/reports/quarter?year=2033&q=4"), "$.income")).isEqualTo("0.00");
        POST("/api/deals/" + d + "/stage", "{\"stage\":\"DONE\",\"eventDate\":\"2033-10-05\"}");
        String q4 = GET("/api/reports/quarter?year=2033&q=4");
        assertThat((String) read(q4, "$.income")).isEqualTo("90.00");
        assertThat((List<?>) read(q4, "$.warnings[?(@ =~ /.*Borclu Alıcı.*/)]")).hasSize(1);
    }
}
