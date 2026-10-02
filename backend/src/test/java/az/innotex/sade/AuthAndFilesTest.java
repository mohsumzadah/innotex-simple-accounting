package az.innotex.sade;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class AuthAndFilesTest extends ApiTest {

    @Test
    void tokensizSorgu401() throws Exception {
        MvcResult r = mvc.perform(get("/api/accounts")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(401);
        assertThat((String) JsonPath.read(r.getResponse().getContentAsString(), "$.error")).isEqualTo("avtorizasiya");
        assertThat(mvc.perform(get("/api/accounts").header("Authorization", "Bearer yanlis")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void girisVeCixis() throws Exception {
        MvcResult bad = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"yanlis-parol\"}")).andReturn();
        assertThat(bad.getResponse().getStatus()).isEqualTo(401);

        String t = JsonPath.read(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn().getResponse().getContentAsString(), "$.token");
        MvcResult me = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + t)).andReturn();
        assertThat((String) JsonPath.read(me.getResponse().getContentAsString(), "$.email")).isEqualTo(EMAIL);

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + t));
        assertThat(mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + t)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void parolDeyisme() throws Exception {
        failPost("/api/auth/password", "{\"current\":\"yanlis\",\"new\":\"yeni-parol-123\"}", 400);
        failPost("/api/auth/password", "{\"current\":\"" + PASSWORD + "\",\"new\":\"qisa\"}", 400);
    }

    @Test
    void faylYuklemeVeEndirme() throws Exception {
        MockMultipartFile f = new MockMultipartFile("file", "qebz.txt", "text/plain", "salam".getBytes());
        MvcResult up = mvc.perform(multipart("/api/files").file(f).header("Authorization", "Bearer " + token())).andReturn();
        assertThat(up.getResponse().getStatus()).isIn(200, 201);
        String body = up.getResponse().getContentAsString();
        assertThat((String) read(body, "$.name")).isEqualTo("qebz.txt");
        assertThat(((Number) read(body, "$.size")).intValue()).isEqualTo(5);

        MvcResult dl = run(get("/api/files/" + id(body)), null);
        assertThat(dl.getResponse().getStatus()).isEqualTo(200);
        assertThat(dl.getResponse().getHeader("Content-Disposition")).contains("qebz.txt");
        assertThat(dl.getResponse().getContentAsString()).isEqualTo("salam");
        assertThat(run(get("/api/files/999999"), null).getResponse().getStatus()).isEqualTo(404);
    }
}
