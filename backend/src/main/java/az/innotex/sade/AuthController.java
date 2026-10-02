package az.innotex.sade;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/login") public Dto.TokenRes login(@RequestBody Dto.LoginReq r) { return new Dto.TokenRes(auth.login(r.email(), r.password())); }

    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest req) { auth.logout(WebConfig.bearer(req)); }

    @GetMapping("/me") public Dto.MeRes me() { return auth.me(); }

    @PostMapping("/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@RequestBody Dto.PasswordReq r) { auth.changePassword(r.current(), r.newPassword()); }
}
