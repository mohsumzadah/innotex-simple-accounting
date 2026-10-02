package az.innotex.sade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    private static ResponseEntity<Map<String, String>> body(HttpStatusCode st, String code, String msg) {
        return ResponseEntity.status(st).body(Map.of("error", code, "message", msg));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, String>> api(ApiException e) { return body(e.status, e.code, e.getMessage()); }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    ResponseEntity<Map<String, String>> bad(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "yanlis_sorgu", "Sorğu yanlışdır: sahələri və formatı yoxlayın");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> big(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "fayl_boyukdur", "Fayl 20 MB-dan böyük ola bilməz");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> integrity(Exception e) {
        log.warn("Bütövlük pozuntusu", e);
        return body(HttpStatus.CONFLICT, "elaqeli_melumat", "Əməliyyat əlaqəli və ya təkrarlanan məlumata görə mümkün deyil");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> other(Exception e) {
        if (e instanceof ErrorResponse er) {
            HttpStatusCode st = er.getStatusCode();
            return body(st, st.value() == 404 ? "tapilmadi" : "sorgu_xetasi", st.value() == 404 ? "Tapılmadı" : "Sorğu icra oluna bilmədi");
        }
        log.error("Gözlənilməz xəta", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "server_xetasi", "Daxili server xətası");
    }
}
