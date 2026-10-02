package az.innotex.sade;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** .docx-də {{placeholder}} doldurma: Word mətni run-lara bölür, ona görə hər abzasın run mətnləri birləşdirilib axtarılır */
final class Docx {
    private static final Pattern PH = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*}}");
    private static final String W = "declare namespace w='http://schemas.openxmlformats.org/wordprocessingml/2006/main' ";
    static final String BLANK = "__________";

    private Docx() {}

    /** Faylı yoxlayır (etibarlı .docx olmalıdır) */
    static void validate(byte[] data) {
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(data))) {
            if (d.getDocument() == null || d.getDocument().getBody() == null) throw new IllegalArgumentException();
        } catch (Exception e) {
            throw ApiException.bad("Fayl etibarlı Word (.docx) sənədi deyil");
        }
    }

    /** Şablonun surətində placeholder-ləri doldurur; naməlum açar olduğu kimi qalır, boş dəyər xətt olur */
    static byte[] fill(byte[] template, Map<String, String> values) {
        try (XWPFDocument d = new XWPFDocument(new ByteArrayInputStream(template)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<XmlObject> roots = new ArrayList<>();
            roots.add(d.getDocument().getBody());
            for (XWPFHeader h : d.getHeaderList()) roots.add(h._getHdrFtr());
            for (XWPFFooter f : d.getFooterList()) roots.add(f._getHdrFtr());
            for (XmlObject root : roots) {
                for (XmlObject p : root.selectPath(W + ".//w:p")) fillParagraph((CTP) p, values);
            }
            d.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Word sənədi doldurulmadı", e);
        }
    }

    private static String text(CTR r) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r.sizeOfTArray(); i++) sb.append(r.getTArray(i).getStringValue());
        return sb.toString();
    }

    static void fillParagraph(CTP p, Map<String, String> values) {
        List<CTR> runs = new ArrayList<>();
        for (CTR r : p.getRList()) if (r.sizeOfTArray() > 0) runs.add(r);
        if (runs.isEmpty()) return;
        List<String> texts = new ArrayList<>();
        StringBuilder all = new StringBuilder();
        List<Integer> starts = new ArrayList<>();
        for (CTR r : runs) { starts.add(all.length()); String t = text(r); texts.add(t); all.append(t); }
        Matcher m = PH.matcher(all);
        List<int[]> spans = new ArrayList<>();
        List<String> repl = new ArrayList<>();
        while (m.find()) {
            String v = values.containsKey(m.group(1)) ? values.get(m.group(1)) : null;
            if (!values.containsKey(m.group(1))) continue; // naməlum açar görünən qalır
            spans.add(new int[]{m.start(), m.end()});
            repl.add(v == null || v.isBlank() ? BLANK : v);
        }
        if (spans.isEmpty()) return;
        StringBuilder[] res = new StringBuilder[runs.size()];
        for (int i = 0; i < res.length; i++) res[i] = new StringBuilder();
        int si = 0;
        for (int i = 0; i < runs.size(); i++) {
            int a = starts.get(i), b = a + texts.get(i).length();
            for (int pos = a; pos < b; pos++) {
                while (si < spans.size() && spans.get(si)[1] <= pos) si++;
                if (si < spans.size() && pos >= spans.get(si)[0]) {
                    if (pos == spans.get(si)[0]) res[i].append(repl.get(si)); // yazı formatı: placeholder-in başladığı run
                } else res[i].append(all.charAt(pos));
            }
        }
        for (int i = 0; i < runs.size(); i++) {
            String nt = res[i].toString();
            if (nt.equals(texts.get(i))) continue;
            CTR r = runs.get(i);
            for (int k = r.sizeOfTArray() - 1; k >= 1; k--) r.removeT(k);
            r.getTArray(0).setStringValue(nt);
            org.apache.xmlbeans.XmlCursor c = r.getTArray(0).newCursor();
            c.setAttributeText(new javax.xml.namespace.QName("http://www.w3.org/XML/1998/namespace", "space"), "preserve");
            c.dispose();
        }
    }
}
