package az.innotex.sade;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseItemsTest extends ApiTest {

    private String item(String name, long acc, String rec) {
        return "{\"name\":\"" + name + "\",\"vendor\":\"Test təchizatçı\",\"category\":\"Server/Hosting\",\"currency\":\"USD\",\"defaultAmount\":\"20.00\","
                + "\"accountId\":" + acc + ",\"deductible\":true,\"recurrence\":\"" + rec + "\",\"active\":true}";
    }

    private List<Map<String, Object>> missing(String month) throws Exception { return read(GET("/api/expense-items/missing?month=" + month), "$"); }

    private boolean has(List<Map<String, Object>> l, long itemId) {
        return l.stream().anyMatch(m -> ((Number) m.get("itemId")).longValue() == itemId);
    }

    @Test
    void crudVeBosluqSiyahisi() throws Exception {
        long acc = newAccount("Maddə bankı", "BANK", "1000.00");
        String created = POST("/api/expense-items", item("Test server", acc, "MONTHLY"));
        long id = id(created);
        assertThat((String) read(created, "$.accountName")).isEqualTo("Maddə bankı");
        assertThat((String) read(created, "$.defaultAmount")).isEqualTo("20.00");
        assertThat((String) read(created, "$.recurrence")).isEqualTo("MONTHLY");

        // ad təkrarı olmaz
        failPost("/api/expense-items", item("Test server", acc, "NONE"), 409);

        String upd = PUT("/api/expense-items/" + id, item("Test server 2", acc, "MONTHLY"));
        assertThat((String) read(upd, "$.name")).isEqualTo("Test server 2");

        // "birdəfəlik" maddə boşluq siyahısında görünmür
        long once = id(POST("/api/expense-items", item("Test birdəfəlik", acc, "NONE")));
        List<Map<String, Object>> m1 = missing("2026-03");
        assertThat(has(m1, id)).isTrue();
        assertThat(has(m1, once)).isFalse();
        assertThat(m1.stream().filter(m -> ((Number) m.get("itemId")).longValue() == id).findFirst().orElseThrow().get("lastDate")).isNull();

        // həmin ayda xərc yazılınca itir; başqa ayda qalır
        String exp = POST("/api/expenses", "{\"date\":\"2026-03-10\",\"vendor\":\"Test təchizatçı\",\"category\":\"Server/Hosting\",\"amount\":\"20.00\","
                + "\"currency\":\"USD\",\"rate\":\"1.7\",\"accountId\":" + acc + ",\"itemId\":" + id + "}");
        long expId = id(exp);
        assertThat(((Number) read(exp, "$.itemId")).longValue()).isEqualTo(id);
        assertThat(has(missing("2026-03"), id)).isFalse();
        List<Map<String, Object>> m2 = missing("2026-04");
        assertThat(has(m2, id)).isTrue();
        Map<String, Object> row = m2.stream().filter(m -> ((Number) m.get("itemId")).longValue() == id).findFirst().orElseThrow();
        assertThat(row.get("lastAmountAzn")).isEqualTo("34.00");
        assertThat(row.get("lastDate")).isEqualTo("2026-03-10");

        // passiv maddə siyahıda olmur
        PUT("/api/expense-items/" + id, item("Test server 2", acc, "MONTHLY").replace("\"active\":true", "\"active\":false"));
        assertThat(has(missing("2026-04"), id)).isFalse();

        // istifadədə olan maddə silinmir (409), istifadəsiz silinir
        String err = failDelete("/api/expense-items/" + id, 409);
        assertThat((String) read(err, "$.message")).contains("passiv");
        DELETE("/api/expense-items/" + once);
        // xərc silinəndən sonra maddə silinə bilər
        DELETE("/api/expenses/" + expId);
        DELETE("/api/expense-items/" + id);
        failDelete("/api/expense-items/" + id, 404);
    }
}
