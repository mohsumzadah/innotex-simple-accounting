package az.innotex.sade;

import org.springframework.http.HttpStatus;

/** Azərbaycan dilində mesajlı API xətası: { "error": kod, "message": mətn } */
public class ApiException extends RuntimeException {
    public final HttpStatus status;
    public final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "yanlis_sorgu", message); }
    public static ApiException notFound(String message) { return new ApiException(HttpStatus.NOT_FOUND, "tapilmadi", message); }
    public static ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
    public static ApiException unauthorized(String message) { return new ApiException(HttpStatus.UNAUTHORIZED, "avtorizasiya", message); }
    public static ApiException forbidden(String message) { return new ApiException(HttpStatus.FORBIDDEN, "icaze_yoxdur", message); }
}
