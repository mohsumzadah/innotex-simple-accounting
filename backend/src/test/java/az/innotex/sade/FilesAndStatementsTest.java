package az.innotex.sade;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Fayllar PostgreSQL-də (bayt-bayt eyni), hesab çıxarışları və ehtiyat nüsxə endpointi */
class FilesAndStatementsTest extends ApiTest {
    @Autowired JdbcTemplate jdbc;

    private MvcResult up(String url, MockMultipartFile f, String... params) throws Exception {
        var b = multipart(url).file(f).header("Authorization", "Bearer " + token());
        for (int i = 0; i < params.length; i += 2) b.param(params[i], params[i + 1]);
        return mvc.perform(b).andReturn();
    }

    @Test
    void faylBazadaSaxlanirVeEynidirBaytlarla() throws Exception {
        byte[] data = new byte[300_000];
        new Random(7).nextBytes(data);
        MvcResult r = up("/api/files", new MockMultipartFile("file", "cixaris.pdf", "application/pdf", data));
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        long id = id(r.getResponse().getContentAsString());

        Long len = jdbc.queryForObject("select octet_length(content) from stored_file where id = ?", Long.class, id);
        assertThat(len).isEqualTo(data.length);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        assertThat(jdbc.queryForObject("select sha256 from stored_file where id = ?", String.class, id)).isEqualTo(sha);
        assertThat(jdbc.queryForObject("select content_type from stored_file where id = ?", String.class, id)).isEqualTo("application/pdf");

        byte[] back = run(get("/api/files/" + id), null).getResponse().getContentAsByteArray();
        assertThat(back).isEqualTo(data);
    }

    @Test
    void cixarisCrudVe409() throws Exception {
        long acc = newAccount("Çıxarış hesabı", "BANK", "0");
        long other = newAccount("Başqa hesab", "BANK", "0");
        byte[] data = "xlsx-mezmun".getBytes();
        MvcResult r = up("/api/accounts/" + acc + "/statements", new MockMultipartFile("file", "iyul.xlsx", "application/octet-stream", data),
                "periodFrom", "2026-07-01", "periodTo", "2026-07-31", "note", "test");
        assertThat(r.getResponse().getStatus()).isEqualTo(201);
        String body = r.getResponse().getContentAsString();
        long sid = id(body);
        long fileId = ((Number) read(body, "$.fileId")).longValue();
        assertThat((String) read(body, "$.fileName")).isEqualTo("iyul.xlsx");

        assertThat((Integer) read(GET("/api/accounts/" + acc + "/statements"), "$.length()")).isEqualTo(1);
        assertThat((Integer) read(GET("/api/accounts/" + other + "/statements"), "$.length()")).isEqualTo(0);
        assertThat(run(get("/api/files/" + fileId), null).getResponse().getContentAsByteArray()).isEqualTo(data);

        // yanlış dövr
        assertThat(up("/api/accounts/" + acc + "/statements", new MockMultipartFile("file", "x.xlsx", "x/y", data),
                "periodFrom", "2026-08-31", "periodTo", "2026-08-01").getResponse().getStatus()).isEqualTo(400);

        // hərəkət çıxarışa bağlanır; başqa hesabın çıxarışı rədd edilir
        String mv = POST("/api/movements", "{\"date\":\"2026-07-10\",\"accountId\":" + acc + ",\"direction\":\"IN\",\"amount\":10,\"purpose\":\"OTHER\",\"statementId\":" + sid + "}");
        long mid = id(mv);
        assertThat(((Number) read(mv, "$.statementId")).longValue()).isEqualTo(sid);
        String mvOther = POST("/api/movements", "{\"date\":\"2026-07-10\",\"accountId\":" + other + ",\"direction\":\"IN\",\"amount\":10,\"purpose\":\"OTHER\"}");
        assertThat(run(post("/api/movements"), "{\"date\":\"2026-07-10\",\"accountId\":" + other + ",\"direction\":\"IN\",\"amount\":10,\"purpose\":\"OTHER\",\"statementId\":" + sid + "}")
                .getResponse().getStatus()).isEqualTo(400);
        assertThat((Integer) read(GET("/api/accounts/" + acc + "/statements"), "$.[0].movementCount")).isEqualTo(1);

        // PUT statementId verir; boş buraxsa bağlantı qalır
        String upd = PUT("/api/movements/" + mid, "{\"date\":\"2026-07-11\",\"accountId\":" + acc + ",\"direction\":\"IN\",\"amount\":12,\"purpose\":\"OTHER\"}");
        assertThat(((Number) read(upd, "$.statementId")).longValue()).isEqualTo(sid);

        // bağlı hərəkət varkən silmək 409
        fail(run(delete("/api/statements/" + sid), null), 409);

        // bağlantını qaldır, sonra sil: fayl da silinir
        String un = PUT("/api/movements/" + mid + "/statement", "{\"statementId\":null}");
        assertThat((Object) read(un, "$.statementId")).isNull();
        DELETE("/api/statements/" + sid);
        assertThat(run(get("/api/files/" + fileId), null).getResponse().getStatus()).isEqualTo(404);
        assertThat(run(get("/api/accounts/999999/statements"), null).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void xercHereketiCixarisaBaglanir() throws Exception {
        long acc = newAccount("Kart", "DIRECTOR_CARD", "0");
        long sid = id(up("/api/accounts/" + acc + "/statements", new MockMultipartFile("file", "kart.pdf", "application/pdf", "pdf".getBytes()),
                "periodFrom", "2026-06-27", "periodTo", "2026-09-27").getResponse().getContentAsString());
        String ex = POST("/api/expenses", "{\"date\":\"2026-08-01\",\"category\":\"Test\",\"amount\":5,\"accountId\":" + acc + ",\"currency\":\"AZN\"}");
        long mid = ((Number) read(GET("/api/movements?accountId=" + acc), "$[0].id")).longValue();
        PUT("/api/movements/" + mid + "/statement", "{\"statementId\":" + sid + "}");
        java.util.List<Object> arr = read(GET("/api/expenses"), "$[?(@.id==" + id(ex) + ")].statementId");
        assertThat(((Number) arr.get(0)).longValue()).isEqualTo(sid);
    }

    @Test
    void ehtiyatNusxeGirisTelebEdir() throws Exception {
        assertThat(mvc.perform(get("/api/backup")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }
}
