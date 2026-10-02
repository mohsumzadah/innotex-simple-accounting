package az.innotex.sade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Birdəfəlik, idempotent köçürmə: content boş olan fayllar FILES_DIR-dən bazaya yazılır. Fayl yoxdursa ERROR loglanır, proqram dayanmır. */
@Component
public class FileMigrator implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(FileMigrator.class);
    private final JdbcTemplate jdbc;
    private final Path dir;

    public FileMigrator(JdbcTemplate jdbc, @Value("${app.files-dir}") String dir) {
        this.jdbc = jdbc;
        this.dir = Path.of(dir).toAbsolutePath();
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id, name, stored_name, size_bytes from stored_file where content is null order by id");
        if (rows.isEmpty()) return;
        log.info("Fayl köçürməsi: {} fayl bazaya yazılacaq ({})", rows.size(), dir);
        int ok = 0, problem = 0;
        for (Map<String, Object> r : rows) {
            long id = ((Number) r.get("id")).longValue();
            String name = (String) r.get("name");
            Path p = dir.resolve((String) r.get("stored_name"));
            try {
                if (!Files.isRegularFile(p)) { log.error("Fayl diskdə yoxdur, content boş qalır: id={} ad={} yol={}", id, name, p); problem++; continue; }
                byte[] data = Files.readAllBytes(p);
                String sha = FileService.sha256(data);
                long dbSize = ((Number) r.get("size_bytes")).longValue();
                if (dbSize != data.length) log.warn("Ölçü uyğunsuzluğu id={}: bazada {}, diskdə {} (diskdəki düzgün qəbul edildi)", id, dbSize, data.length);
                jdbc.update("update stored_file set content = ?, size_bytes = ?, sha256 = ?, content_type = ? where id = ? and content is null",
                        data, data.length, sha, FileService.typeByName(name), id);
                String back = jdbc.queryForObject("select encode(sha256(content), 'hex') from stored_file where id = ?", String.class, id);
                if (!sha.equals(back)) { log.error("SHA-256 uyğun gəlmir id={} disk={} baza={}", id, sha, back); problem++; continue; }
                log.info("Köçürüldü: id={} ad={} {} bayt sha256={}", id, name, data.length, sha);
                ok++;
            } catch (Exception ex) {
                log.error("Köçürmə xətası id={} ad={}: {}", id, name, ex.toString());
                problem++;
            }
        }
        log.info("Fayl köçürməsi bitdi: uğurlu={}, problemli={}", ok, problem);
    }
}
