package az.innotex.sade;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileService svc;

    public FileController(FileService svc) { this.svc = svc; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Dto.FileRes upload(@RequestParam("file") MultipartFile file) { return svc.store(file); }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        E.StoredFile f = svc.meta(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.name, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(f.sizeBytes)
                .body(svc.resource(f));
    }
}
