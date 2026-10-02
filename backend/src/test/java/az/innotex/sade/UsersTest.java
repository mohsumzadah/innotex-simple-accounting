package az.innotex.sade;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class UsersTest extends ApiTest {

    private String login(String email, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")).andReturn();
        return r.getResponse().getStatus() == 200 ? read(r.getResponse().getContentAsString(), "$.token") : null;
    }

    private int status(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, String token, String json) throws Exception {
        b.header("Authorization", "Bearer " + token);
        if (json != null) b.contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(b).andReturn().getResponse().getStatus();
    }

    @Test
    void muhasibYaratmaVeIcazeler() throws Exception {
        String me = GET("/api/auth/me");
        assertThat((String) read(me, "$.role")).isEqualTo("ADMIN");

        String u = POST("/api/users", "{\"email\":\"Muhasib1@Test.az\",\"name\":\"Test Mühasib\",\"role\":\"ACCOUNTANT\",\"password\":\"muhasib-parol-1\"}");
        long id = id(u);
        assertThat((String) read(u, "$.email")).isEqualTo("muhasib1@test.az");
        assertThat((String) read(u, "$.role")).isEqualTo("ACCOUNTANT");
        assertThat((Boolean) read(u, "$.active")).isTrue();
        assertThat(String.valueOf((Object) read(GET("/api/users"), "$[?(@.id==" + id + ")].name"))).contains("Test Mühasib");

        failPost("/api/users", "{\"email\":\"muhasib1@test.az\",\"role\":\"ACCOUNTANT\",\"password\":\"muhasib-parol-1\"}", 409);
        failPost("/api/users", "{\"email\":\"diger@test.az\",\"role\":\"ACCOUNTANT\",\"password\":\"qisa\"}", 400);
        failPost("/api/users", "{\"email\":\"diger@test.az\",\"role\":\"BOSS\",\"password\":\"muhasib-parol-1\"}", 400);
        failPost("/api/users", "{\"email\":\"yanlis\",\"role\":\"ACCOUNTANT\",\"password\":\"muhasib-parol-1\"}", 400);

        // Mühasib uçotla işləyir, istifadəçiləri idarə edə bilmir
        String t = login("muhasib1@test.az", "muhasib-parol-1");
        assertThat(t).isNotNull();
        MvcResult m = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + t)).andReturn();
        assertThat((String) read(m.getResponse().getContentAsString(), "$.role")).isEqualTo("ACCOUNTANT");
        assertThat(status(get("/api/accounts"), t, null)).isEqualTo(200);
        assertThat(status(get("/api/users"), t, null)).isEqualTo(403);
        assertThat(status(post("/api/users"), t, "{\"email\":\"x@test.az\",\"role\":\"ADMIN\",\"password\":\"muhasib-parol-1\"}")).isEqualTo(403);

        // Mühasib öz parolunu dəyişə bilir
        assertThat(status(post("/api/auth/password"), t, "{\"current\":\"muhasib-parol-1\",\"new\":\"muhasib-parol-2\"}")).isEqualTo(204);
        assertThat(login("muhasib1@test.az", "muhasib-parol-2")).isNotNull();

        // Passiv edilən istifadəçinin sessiyası bağlanır, girişi olmur
        PUT("/api/users/" + id, "{\"active\":false}");
        assertThat(status(get("/api/accounts"), t, null)).isEqualTo(401);
        assertThat(login("muhasib1@test.az", "muhasib-parol-2")).isNull();

        // Admin parolu sıfırlayır və yenidən aktiv edir
        PUT("/api/users/" + id, "{\"active\":true,\"password\":\"yeni-parol-123\"}");
        assertThat(login("muhasib1@test.az", "yeni-parol-123")).isNotNull();

        DELETE("/api/users/" + id);
        assertThat(login("muhasib1@test.az", "yeni-parol-123")).isNull();
    }

    @Test
    void sonAdminQorunur() throws Exception {
        long self = ((Number) read(GET("/api/auth/me"), "$.id")).longValue();
        fail(run(put("/api/users/" + self), "{\"active\":false}"), 409);
        fail(run(put("/api/users/" + self), "{\"role\":\"ACCOUNTANT\"}"), 409);
        failDelete("/api/users/" + self, 409);
        fail(run(put("/api/users/999999"), "{\"name\":\"x\"}"), 404);
    }
}
