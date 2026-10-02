package az.innotex.sade;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Fayllar PostgreSQL-də saxlanılır (stored_file.content, bytea), maks. 20 MB.
 * FILES_DIR yalnız köhnə sətirlər üçün (content hələ boşdursa) oxuma ehtiyatıdır; yeni fayllar diskə yazılmır.
 */
@Service
public class FileService {
    static final long MAX = 20L * 1024 * 1024;
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"), Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"), Map.entry("gif", "image/gif"), Map.entry("webp", "image/webp"),
            Map.entry("txt", "text/plain"), Map.entry("csv", "text/csv"), Map.entry("html", "text/html"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("doc", "application/msword"), Map.entry("zip", "application/zip"));

    private final R.Files files;
    private final JdbcTemplate jdbc;
    private final Path dir;

    public FileService(R.Files files, JdbcTemplate jdbc, @Value("${app.files-dir}") String dir) {
        this.files = files;
        this.jdbc = jdbc;
        this.dir = Path.of(dir).toAbsolutePath();
    }

    static String typeByName(String name) {
        if (name == null) return "application/octet-stream";
        int i = name.lastIndexOf('.');
        String ext = i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
        return TYPES.getOrDefault(ext, "application/octet-stream");
    }

    static String sha256(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    public Dto.FileRes store(MultipartFile f) {
        if (f == null || f.isEmpty()) throw ApiException.bad("Fayl seçilməyib");
        if (f.getSize() > MAX) throw ApiException.bad("Fayl 20 MB-dan böyük ola bilməz");
        String n = f.getOriginalFilename();
        String name = n == null || n.isBlank() ? "fayl" : Path.of(n.replace('\\', '/')).getFileName().toString();
        try {
            E.StoredFile e = storeBytes(f.getBytes(), name);
            return new Dto.FileRes(e.id, e.name, e.sizeBytes);
        } catch (IOException ex) {
            throw new IllegalStateException("Fayl saxlanmadı", ex);
        }
    }

    /** Baytları yalnız bazada saxlayır (yüklənmiş fayl və ya doldurulmuş sənəd) */
    public E.StoredFile storeBytes(byte[] data, String name) {
        if (data.length > MAX) throw ApiException.bad("Fayl 20 MB-dan böyük ola bilməz");
        E.StoredFile e = new E.StoredFile();
        e.storedName = UUID.randomUUID().toString();
        e.name = name;
        e.sizeBytes = data.length;
        e.contentType = typeByName(name);
        e.sha256 = sha256(data);
        e.createdAt = LocalDateTime.now();
        e = files.save(e);
        jdbc.update("update stored_file set content = ? where id = ?", data, e.id);
        return e;
    }

    public byte[] bytes(Long id) {
        E.StoredFile m = meta(id);
        List<byte[]> r = jdbc.query("select content from stored_file where id = ?", (rs, i) -> rs.getBytes(1), id);
        if (!r.isEmpty() && r.get(0) != null) return r.get(0);
        // ehtiyat: məzmun hələ bazaya köçürülməyibsə diskdən oxu
        try { return Files.readAllBytes(dir.resolve(m.storedName)); }
        catch (IOException ex) { throw ApiException.notFound("Faylın məzmunu tapılmadı"); }
    }

    public void delete(Long id) {
        if (id == null) return;
        files.findById(id).ifPresent(files::delete);   // diskdəki köhnə nüsxəyə toxunulmur
    }

    private static final String[][] REFS = {{"movement", "file_id"}, {"expense", "file_id"}, {"payment_allocation", "file_id"},
            {"deal", "advance_file_id"}, {"deal", "act_file_id"}, {"deal", "invoice_file_id"}, {"deal", "payment_file_id"},
            {"deal_document", "file_id"}, {"deal_document", "previous_file_id"}, {"template", "file_id"}, {"account_statement", "file_id"},
            {"payroll_payment", "file_id"}, {"payroll_run_file", "file_id"}};

    /** Fayla hələ başqa sətir işarə edirmi (silməzdən əvvəl yoxlanılır) */
    public boolean isReferenced(Long id) {
        for (String[] r : REFS) {
            Integer n = jdbc.queryForObject("select count(*) from " + r[0] + " where " + r[1] + " = ?", Integer.class, id);
            if (n != null && n > 0) return true;
        }
        return false;
    }

    public E.StoredFile meta(Long id) { return files.findById(id).orElseThrow(() -> ApiException.notFound("Fayl tapılmadı")); }

    public Resource resource(E.StoredFile f) { return new ByteArrayResource(bytes(f.id)); }
}
