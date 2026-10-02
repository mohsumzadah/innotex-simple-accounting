package az.innotex.sade;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Hazır (şablonsuz) sənəd yükləmə: DOCX və PDF */
class UploadedDocTest extends ApiTest {

    private MockHttpServletResponse up(String path, MockMultipartFile f, String... kv) throws Exception {
        var b = multipart(path).file(f);
        for (int i = 0; i < kv.length; i += 2) b.param(kv[i], kv[i + 1]);
        return mvc.perform(b.header("Authorization", "Bearer " + token())).andReturn().getResponse();
    }

    private static byte[] docx() throws Exception {
        try (XWPFDocument d = new XWPFDocument(); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            d.createParagraph().createRun().setText("Hazır müqavilə");
            d.write(o);
            return o.toByteArray();
        }
    }

    @Test
    void hazirSenedYukle() throws Exception {
        long deal = newDeal(newCustomer("Yükləmə Müştərisi"), "10.00");
        String u = "/api/deals/" + deal + "/documents/upload";
        var r1 = up(u, new MockMultipartFile("file", "Mənim müqavilə.docx", "application/octet-stream", docx()), "code", "CONTRACT");
        assertThat(r1.getStatus()).isEqualTo(201);
        String j1 = r1.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat((String) read(j1, "$.format")).isEqualTo("DOCX");
        assertThat((String) read(j1, "$.title")).isEqualTo("Mənim müqavilə");
        assertThat((Object) read(j1, "$.templateId")).isNull();
        long d1 = id(j1);

        byte[] pdf = "%PDF-1.4\n%test\n".getBytes();
        var r2 = up(u, new MockMultipartFile("file", "akt.pdf", "application/pdf", pdf), "code", "ACT", "title", "İmzalı akt");
        assertThat(r2.getStatus()).isEqualTo(201);
        String j2 = r2.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat((String) read(j2, "$.format")).isEqualTo("PDF");
        assertThat((String) read(j2, "$.title")).isEqualTo("İmzalı akt");
        long d2 = id(j2);

        List<Map<String, Object>> list = read(GET("/api/deals/" + deal + "/documents"), "$");
        assertThat(list).hasSize(2);

        // yanlış növ / fayl
        assertThat(up(u, new MockMultipartFile("file", "x.txt", "text/plain", "abc".getBytes()), "code", "CONTRACT").getStatus()).isEqualTo(400);
        assertThat(up(u, new MockMultipartFile("file", "x.pdf", "application/pdf", "abc".getBytes()), "code", "CONTRACT").getStatus()).isEqualTo(400);
        assertThat(up(u, new MockMultipartFile("file", "x.pdf", "application/pdf", pdf), "code", "XYZ").getStatus()).isEqualTo(400);

        // refill: şablondan yaradılmayıb
        fail(run(post("/api/deal-documents/" + d1 + "/refill"), null), 400);
        fail(run(post("/api/deal-documents/" + d2 + "/refill"), null), 400);

        // PDF endirmə: düzgün Content-Type
        var dl = run(get("/api/deal-documents/" + d2 + "/docx"), null).getResponse();
        assertThat(dl.getContentType()).isEqualTo("application/pdf");
        assertThat(dl.getContentAsByteArray()).isEqualTo(pdf);
        assertThat(dl.getHeader("Content-Disposition")).contains(".pdf");

        // yeni versiya: PDF sənədə .docx yüklənmir, .pdf yüklənir; geri qayıtmaq olur
        assertThat(up("/api/deal-documents/" + d2 + "/upload", new MockMultipartFile("file", "a.docx", "application/octet-stream", docx())).getStatus()).isEqualTo(400);
        byte[] pdf2 = "%PDF-1.7\nikinci\n".getBytes();
        assertThat(up("/api/deal-documents/" + d2 + "/upload", new MockMultipartFile("file", "a2.pdf", "application/pdf", pdf2)).getStatus()).isEqualTo(200);
        assertThat(run(get("/api/deal-documents/" + d2 + "/docx"), null).getResponse().getContentAsByteArray()).isEqualTo(pdf2);
        POST("/api/deal-documents/" + d2 + "/revert", null);
        assertThat(run(get("/api/deal-documents/" + d2 + "/docx"), null).getResponse().getContentAsByteArray()).isEqualTo(pdf);

        // yükləndikdən sonra ad dəyişir, fayl dəyişmir
        assertThat((String) read(PUT("/api/deal-documents/" + d2, "{\"title\":\"  Müqaviləyə əlavə №1 \"}"), "$.title")).isEqualTo("Müqaviləyə əlavə №1");
        assertThat(run(get("/api/deal-documents/" + d2 + "/docx"), null).getResponse().getContentAsByteArray()).isEqualTo(pdf);
        fail(run(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/deal-documents/" + d2), "{\"title\":\" \"}"), 400);
    }

    @Test
    void htmlSenedinAdiDeyisir() throws Exception {
        long deal = newDeal(newCustomer("Ad Müştərisi"), "10.00");
        String doc = POST("/api/deals/" + deal + "/documents", "{\"code\":\"CONTRACT\"}");
        long id = id(doc);
        String html = read(doc, "$.html");
        // yalnız ad göndərilir: mətn qalır
        String r = PUT("/api/deal-documents/" + id, "{\"title\":\"Yeni ad\"}");
        assertThat((String) read(r, "$.title")).isEqualTo("Yeni ad");
        assertThat((String) read(r, "$.html")).isEqualTo(html);
    }
}
