package az.innotex.sade;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** İnteqrasiya testlərinin bazası: real HTTP (MockMvc) + H2 (PostgreSQL rejimi). Test məlumatları uydurmadır. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class ApiTest {
    static final String EMAIL = "test@sade.az";
    static final String PASSWORD = "test-parol-123";

    @Autowired protected MockMvc mvc;
    private static String token;

    protected synchronized String token() throws Exception {
        if (token == null) {
            String r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            token = JsonPath.read(r, "$.token");
        }
        return token;
    }

    protected MvcResult run(MockHttpServletRequestBuilder b, String json) throws Exception {
        b.header("Authorization", "Bearer " + token());
        if (json != null) b.contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(b).andReturn();
    }

    private String ok(MvcResult r) throws Exception {
        int s = r.getResponse().getStatus();
        if (s < 200 || s > 299) throw new AssertionError("HTTP " + s + ": " + r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        return r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    protected String GET(String path) throws Exception { return ok(run(get(path), null)); }
    protected String POST(String path, String json) throws Exception { return ok(run(post(path), json)); }
    protected String PUT(String path, String json) throws Exception { return ok(run(put(path), json)); }
    protected String DELETE(String path) throws Exception { return ok(run(delete(path), null)); }

    /** Gözlənilən xəta statusu; cavab { error, message } olmalıdır */
    protected String fail(MvcResult r, int status) throws Exception {
        String body = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        if (r.getResponse().getStatus() != status) throw new AssertionError("Gözlənilən " + status + ", alındı " + r.getResponse().getStatus() + ": " + body);
        if (!JsonPath.read(body, "$.error").toString().isBlank() && JsonPath.read(body, "$.message").toString().isBlank()) throw new AssertionError("Mesaj boşdur");
        return body;
    }
    protected String failPost(String path, String json, int status) throws Exception { return fail(run(post(path), json), status); }
    protected String failDelete(String path, int status) throws Exception { return fail(run(delete(path), null), status); }

    protected static <T> T read(String json, String path) { return JsonPath.read(json, path); }
    protected static long id(String json) { return ((Number) JsonPath.read(json, "$.id")).longValue(); }

    protected long newAccount(String name, String type, String opening) throws Exception {
        return id(POST("/api/accounts", "{\"name\":\"" + name + "\",\"type\":\"" + type + "\",\"openingBalance\":\"" + opening
                + "\",\"openingDate\":\"2026-01-01\",\"currency\":\"AZN\"}"));
    }

    protected long newCustomer(String name) throws Exception {
        return id(POST("/api/customers", "{\"name\":\"" + name + "\",\"voen\":\"0000000001\",\"director\":\"Test Direktor\"}"));
    }

    protected long newDeal(long customerId, String price) throws Exception {
        return id(POST("/api/deals", "{\"customerId\":" + customerId + ",\"product\":\"\",\"description\":\"\",\"price\":\"" + price + "\",\"computers\":2}"));
    }
}
