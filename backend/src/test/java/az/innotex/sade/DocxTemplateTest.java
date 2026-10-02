package az.innotex.sade;

import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

class DocxTemplateTest extends ApiTest {

    private static byte[] template() throws Exception {
        try (XWPFDocument d = new XWPFDocument(); ByteArrayOutputStream o = new ByteArrayOutputStream()) {
            // placeholder 3 run-a bölünüb; ilk run qalındır
            XWPFParagraph p = d.createParagraph();
            XWPFRun a = p.createRun(); a.setBold(true); a.setText("Tərəf: {{cust");
            XWPFRun b = p.createRun(); b.setText("omer.");
            XWPFRun c = p.createRun(); c.setItalic(true); c.setText("name}} və {{naməlum.açar}}. Məbləğ {{deal.priceWords}}.");
            XWPFTable t = d.createTable(1, 1);
            XWPFTableCell cell = t.getRow(0).getCell(0);
            cell.getParagraphs().get(0).createRun().setText("VÖEN {{customer.voen}}");
            XWPFTable inner = cell.insertNewTbl(cell.getParagraphs().get(0).getCTP().newCursor());
            inner.createRow().createCell().setText("İç {{deal.contractNo}}");
            XWPFHeader h = d.createHeader(HeaderFooterType.DEFAULT);
            XWPFParagraph hp = h.createParagraph();
            hp.createRun().setText("Baş {{comp");
            hp.createRun().setText("any.name}}");
            d.write(o);
            return o.toByteArray();
        }
    }

    private static String allText(byte[] docx) throws Exception {
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(docx))) {
            StringBuilder sb = new StringBuilder();
            d.getParagraphs().forEach(p -> sb.append(p.getText()).append('\n'));
            d.getTables().forEach(t -> t.getRows().forEach(r -> r.getTableCells().forEach(c -> {
                c.getParagraphs().forEach(p -> sb.append(p.getText()).append('\n'));
                c.getTables().forEach(n -> n.getRows().forEach(nr -> nr.getTableCells().forEach(nc -> sb.append(nc.getText()).append('\n'))));
            })));
            d.getHeaderList().forEach(h -> sb.append(h.getText()));
            return sb.toString();
        }
    }

    @Test
    void docxSablonDoldurulurSablonDeyismir() throws Exception {
        PUT("/api/settings", "{\"companyName\":\"Test Təchizatçı MMC\",\"voen\":\"1111111111\",\"address\":\"A\",\"director\":\"D\",\"bank\":\"B\",\"iban\":\"I\","
                + "\"bankCode\":\"0\",\"swift\":\"S\",\"phone\":\"1\",\"email\":\"t@example.az\",\"profitTaxRate\":\"20.00\"}");
        byte[] tpl = template();
        var upRes = up(multipart("/api/files").file(new MockMultipartFile("file", "sablon.docx", "application/octet-stream", tpl))).getResponse();
        long fileId = id(upRes.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));

        String created = POST("/api/templates", "{\"code\":\"CONTRACT\",\"title\":\"Word test\",\"format\":\"DOCX\",\"fileId\":" + fileId + ",\"isDefault\":false}");
        long tplId = id(created);
        assertThat((String) read(created, "$.format")).isEqualTo("DOCX");
        assertThat((Object) read(created, "$.html")).isNull();
        failPost("/api/templates", "{\"code\":\"CONTRACT\",\"title\":\"x\",\"format\":\"DOCX\"}", 400);

        long cust = id(POST("/api/customers", "{\"name\":\"Test Müştəri MMC\",\"voen\":\"2222222222\",\"director\":\"Test\"}"));
        long deal = newDeal(cust, "1250.50");
        POST("/api/deals/" + deal + "/stage", "{\"stage\":\"CONTRACT\",\"contractNo\":\"\",\"contractDate\":\"2026-03-05\"}");
        String contractNo = read(GET("/api/deals/" + deal), "$.contractNo");

        String doc = POST("/api/deals/" + deal + "/documents", "{\"code\":\"CONTRACT\",\"templateId\":" + tplId + "}");
        long docId = id(doc);
        assertThat((String) read(doc, "$.format")).isEqualTo("DOCX");
        assertThat((Boolean) read(doc, "$.hasPrevious")).isFalse();

        var dl = run(get("/api/deal-documents/" + docId + "/docx"), null).getResponse();
        assertThat(dl.getStatus()).isEqualTo(200);
        assertThat(dl.getHeader("Content-Disposition")).contains("filename*=UTF-8''").contains("M%C3%BCqavil%C9%99");
        byte[] out = dl.getContentAsByteArray();
        String text = allText(out);
        assertThat(text).contains("Tərəf: Test Müştəri MMC və {{naməlum.açar}}. Məbləğ min iki yüz əlli manat 50 qəpik.")
                .contains("VÖEN 2222222222").contains("İç " + contractNo).contains("Baş Test Təchizatçı MMC")
                .doesNotContain("{{customer").doesNotContain("{{deal.").doesNotContain("{{company");
        // formatlama: ilk run qalın qalır
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(out))) {
            XWPFRun first = d.getParagraphs().get(0).getRuns().get(0);
            assertThat(first.isBold()).isTrue();
            assertThat(first.text()).startsWith("Tərəf: Test Müştəri MMC");
        }
        // şablon faylı dəyişməyib
        var orig = run(get("/api/files/" + fileId), null).getResponse().getContentAsByteArray();
        assertThat(Arrays.equals(orig, tpl)).isTrue();


        // redaktə olunmuş fayl yüklə, sonra bir addım geri
        byte[] edited = template();
        String upl = ok(up(multipart("/api/deal-documents/" + docId + "/upload").file(new MockMultipartFile("file", "duzelis.docx", "application/octet-stream", edited))));
        assertThat((Boolean) read(upl, "$.hasPrevious")).isTrue();
        assertThat(allText(run(get("/api/deal-documents/" + docId + "/docx"), null).getResponse().getContentAsByteArray())).contains("{{comp");
        String rev = POST("/api/deal-documents/" + docId + "/revert", "{}");
        assertThat(allText(run(get("/api/deal-documents/" + docId + "/docx"), null).getResponse().getContentAsByteArray())).contains("Test Müştəri MMC");
        // yenidən doldur: şablondan təzələnir
        String refilled = POST("/api/deal-documents/" + docId + "/refill", "{}");
        assertThat((Boolean) read(refilled, "$.hasPrevious")).isTrue();
        // .docx olmayan fayl rədd edilir
        assertThat(up(multipart("/api/deal-documents/" + docId + "/upload").file(new MockMultipartFile("file", "x.txt", "text/plain", "abc".getBytes()))).getResponse().getStatus()).isEqualTo(400);

        DELETE("/api/deal-documents/" + docId);
        assertThat(Files.exists(Path.of("target/test-files"))).isFalse();   // fayllar yalnız bazada saxlanılır
    }

    private org.springframework.test.web.servlet.MvcResult up(org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder b) throws Exception {
        return mvc.perform(b.header("Authorization", "Bearer " + token())).andReturn();
    }

    private String ok(org.springframework.test.web.servlet.MvcResult r) throws Exception {
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
