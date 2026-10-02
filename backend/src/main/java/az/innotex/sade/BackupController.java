package az.innotex.sade;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Təzə pg_dump -Fc nüsxəsini yükləmək üçün verir (giriş tələb olunur, WebConfig interceptor-u ilə) */
@RestController
@RequestMapping("/api")
public class BackupController {
    private final String url, user, password;

    public BackupController(@Value("${spring.datasource.url}") String url,
                            @Value("${spring.datasource.username}") String user,
                            @Value("${spring.datasource.password:}") String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    @GetMapping("/backup")
    public ResponseEntity<FileSystemResource> backup() throws IOException, InterruptedException {
        if (!url.startsWith("jdbc:postgresql:")) throw ApiException.bad("Ehtiyat nüsxə yalnız PostgreSQL ilə işləyir");
        URI u = URI.create(url.substring(5));
        String host = u.getHost() == null ? "localhost" : u.getHost();
        int port = u.getPort() < 0 ? 5432 : u.getPort();
        String db = u.getPath().startsWith("/") ? u.getPath().substring(1) : u.getPath();
        Path tmp = Files.createTempFile("sade-backup-", ".dump");
        ProcessBuilder pb = new ProcessBuilder("pg_dump", "-Fc", "-h", host, "-p", String.valueOf(port), "-U", user, "-d", db, "-f", tmp.toString());
        pb.environment().put("PGPASSWORD", password);
        pb.redirectErrorStream(true);
        Process p;
        try { p = pb.start(); }
        catch (IOException ex) { Files.deleteIfExists(tmp); throw new IllegalStateException("pg_dump tapılmadı", ex); }
        String out;
        try (InputStream in = p.getInputStream()) { out = new String(in.readAllBytes()); }
        if (p.waitFor() != 0) {
            Files.deleteIfExists(tmp);
            throw new IllegalStateException("pg_dump alınmadı: " + out);
        }
        String name = "innotex-sade-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")) + ".dump";
        FileSystemResource res = new FileSystemResource(tmp) {
            @Override public InputStream getInputStream() throws IOException {
                return new FilterInputStream(super.getInputStream()) {
                    @Override public void close() throws IOException { super.close(); Files.deleteIfExists(tmp); }
                };
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(tmp))
                .body(res);
    }
}
