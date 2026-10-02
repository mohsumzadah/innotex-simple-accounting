package az.innotex.sade;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Mərhələni öz panelindən tamamlamaq (complete) və istəyə bağlı mərhələni ötürmək (skip) */
class StageFlowTest extends ApiTest {
    private long deal(String terms, String percent) throws Exception {
        long cust = newCustomer("Mərhələ Müştəri " + terms + percent);
        return id(POST("/api/deals", "{\"customerId\":" + cust + ",\"price\":\"100.00\",\"computers\":1,\"paymentTerms\":\"" + terms + "\""
                + (percent == null ? "" : ",\"advancePercent\":" + percent) + "}"));
    }

    private String stage(String json) { return read(json, "$.stage"); }

    @Test
    void prepaidComplete() throws Exception {
        long id = deal("PREPAID", null);
        String u = "/api/deals/" + id;
        String d = POST(u + "/complete", "{\"eventDate\":\"2026-06-01\",\"note\":\"ok\"}");
        assertThat(stage(d)).isEqualTo("CONTRACT");
        // müqavilə nömrəsi cari mərhələ sahəsi kimi yazılır və növbəti mərhələyə keçir
        d = POST(u + "/complete", "{\"contractNo\":\"M-1\",\"contractDate\":\"2026-06-02\"}");
        assertThat(stage(d)).isEqualTo("PROTOCOL");
        assertThat((String) read(d, "$.contractNo")).isEqualTo("M-1");
        d = POST(u + "/skip", "{}");
        assertThat(stage(d)).isEqualTo("ADVANCE_INVOICE");
        d = POST(u + "/complete", "{\"advanceInvoiceNo\":\"A-1\"}");
        assertThat(stage(d)).isEqualTo("PAID");
        assertThat((String) read(d, "$.advanceInvoiceNo")).isEqualTo("A-1");
        assertThat(stage(POST(u + "/complete", "{}"))).isEqualTo("IN_PROGRESS");
        assertThat(stage(POST(u + "/complete", "{}"))).isEqualTo("ACT");
        assertThat(stage(POST(u + "/complete", "{\"actNo\":\"T-1\"}"))).isEqualTo("INVOICE");
        d = POST(u + "/complete", "{\"invoiceNo\":\"Q-1\"}");
        assertThat(stage(d)).isEqualTo("DONE");
        assertThat((String) read(d, "$.invoiceAmount")).isEqualTo("100.00");
        failPost(u + "/complete", "{}", 400);
        List<Map<String, Object>> ev = read(GET(u + "/events"), "$");
        assertThat(ev.get(0)).containsEntry("toStage", "DONE").containsEntry("fromStage", "INVOICE");
    }

    @Test
    void partialSkipsFinalPaymentIndicator() throws Exception {
        long id = deal("PARTIAL", "50");
        String u = "/api/deals/" + id;
        POST(u + "/complete", "{}");
        POST(u + "/complete", "{}");
        assertThat(stage(POST(u + "/skip", "{}"))).isEqualTo("ADVANCE_INVOICE");
        assertThat(stage(POST(u + "/complete", "{}"))).isEqualTo("PAID");
        POST(u + "/complete", "{}");
        POST(u + "/complete", "{}");
        POST(u + "/complete", "{}");
        assertThat(stage(POST(u + "/complete", "{}"))).isEqualTo("DONE");   // INVOICE -> (FINAL_PAYMENT göstərici) -> DONE
    }

    @Test
    void postpaidOrderAndSkipRules() throws Exception {
        long id = deal("POSTPAID", null);
        String u = "/api/deals/" + id;
        failPost(u + "/skip", "{}", 400);   // NEW istəyə bağlı deyil
        POST(u + "/complete", "{}");
        failPost(u + "/skip", "{}", 400);   // CONTRACT istəyə bağlı deyil
        POST(u + "/complete", "{}");
        assertThat(stage(POST(u + "/skip", "{}"))).isEqualTo("IN_PROGRESS");   // PROTOCOL -> İcra (avans qaiməsi yoxdur)
        assertThat(stage(POST(u + "/complete", "{}"))).isEqualTo("ACT");
        assertThat(stage(POST(u + "/complete", "{}"))).isEqualTo("INVOICE");
        assertThat(stage(POST(u + "/complete", "{}"))).isEqualTo("PAID");
        assertThat(stage(POST(u + "/complete", "{}"))).isEqualTo("DONE");
    }

    @Test
    void saveWithoutMoving() throws Exception {
        long id = deal("POSTPAID", null);
        String u = "/api/deals/" + id;
        POST(u + "/complete", "{}");
        POST(u + "/complete", "{}");   // PROTOCOL
        // keçmiş mərhələnin sahəsi cari mərhələni dəyişmədən yazılır
        String d = POST(u + "/fields", "{\"stage\":\"CONTRACT\",\"contractNo\":\"X-7\"}");
        assertThat(stage(d)).isEqualTo("PROTOCOL");
        assertThat((String) read(d, "$.contractNo")).isEqualTo("X-7");
        d = POST(u + "/fields", "{\"protocolDate\":\"2026-05-05\"}");
        assertThat(stage(d)).isEqualTo("PROTOCOL");
        assertThat((String) read(d, "$.protocolDate")).isEqualTo("2026-05-05");
    }
}
