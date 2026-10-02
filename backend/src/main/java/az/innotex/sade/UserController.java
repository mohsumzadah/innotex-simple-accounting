package az.innotex.sade;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** İstifadəçilərin idarəsi: yalnız ADMIN (yoxlama servisdədir) */
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final AuthService auth;

    public UserController(AuthService auth) { this.auth = auth; }

    @GetMapping public List<Dto.UserRes> list() { return auth.listUsers(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public Dto.UserRes create(@RequestBody Dto.UserReq r) { return auth.createUser(r); }
    @PutMapping("/{id}") public Dto.UserRes update(@PathVariable Long id, @RequestBody Dto.UserReq r) { return auth.updateUser(id, r); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id) { auth.deleteUser(id); }
}
