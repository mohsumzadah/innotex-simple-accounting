package az.innotex.sade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** İstifadəçilər və sessiyalar: parol bcrypt, sessiya tokeni DB-də yalnız SHA-256. Rollar: ADMIN | ACCOUNTANT */
@Service
public class AuthService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    static final String ADMIN = "ADMIN", ACCOUNTANT = "ACCOUNTANT";
    private static final Set<String> ROLES = Set.of(ADMIN, ACCOUNTANT);
    private static final String USER_ATTR = "sade.userId";
    private final R.Users users;
    private final R.Sessions sessions;
    private final BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
    private final SecureRandom rnd = new SecureRandom();
    private final String adminEmail, adminPassword;
    private final long sessionHours;

    public AuthService(R.Users users, R.Sessions sessions,
                       @Value("${app.admin-email:}") String adminEmail,
                       @Value("${app.admin-password:}") String adminPassword,
                       @Value("${app.session-hours:24}") long sessionHours) {
        this.users = users;
        this.sessions = sessions;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.sessionHours = sessionHours;
    }

    /** İlk başlanğıcda ADMIN_EMAIL / ADMIN_PASSWORD ilə ADMIN istifadəçi yaradılır */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("ADMIN_EMAIL / ADMIN_PASSWORD verilməyib: istifadəçi yaradılmadı, giriş mümkün olmayacaq");
            return;
        }
        E.AppUser u = new E.AppUser();
        u.email = adminEmail.trim().toLowerCase();
        u.passwordHash = enc.encode(adminPassword);
        u.role = ADMIN;
        u.createdAt = LocalDateTime.now();
        users.save(u);
        log.info("İlk istifadəçi yaradıldı: {}", u.email);
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @Transactional
    public String login(String email, String password) {
        E.AppUser u = email == null || password == null ? null : users.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (u == null || !enc.matches(password, u.passwordHash)) throw ApiException.unauthorized("E-poçt və ya parol yanlışdır");
        if (!u.active) throw ApiException.unauthorized("İstifadəçi passivdir, administratora müraciət edin");
        byte[] b = new byte[32];
        rnd.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        E.SessionToken s = new E.SessionToken();
        s.tokenHash = sha256(token);
        s.userId = u.id;
        s.createdAt = LocalDateTime.now();
        sessions.save(s);
        return token;
    }

    /** Token etibarlıdırsa istifadəçini sorğuya bağlayır və true qaytarır (vaxtı keçmişlər silinir) */
    @Transactional
    public boolean authenticate(String token) {
        if (token == null || token.isBlank()) return false;
        E.SessionToken s = sessions.findByTokenHash(sha256(token)).orElse(null);
        if (s == null) return false;
        if (s.createdAt.plusHours(sessionHours).isBefore(LocalDateTime.now())) { sessions.delete(s); return false; }
        E.AppUser u = users.findById(s.userId).orElse(null);
        if (u == null || !u.active) { sessions.delete(s); return false; }
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        if (ra != null) ra.setAttribute(USER_ATTR, u.id, RequestAttributes.SCOPE_REQUEST);
        return true;
    }

    @Transactional
    public void logout(String token) {
        if (token != null) sessions.findByTokenHash(sha256(token)).ifPresent(sessions::delete);
    }

    /** Cari sorğunun istifadəçisi */
    public E.AppUser current() {
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        Object id = ra == null ? null : ra.getAttribute(USER_ATTR, RequestAttributes.SCOPE_REQUEST);
        if (id == null) throw ApiException.unauthorized("Giriş tələb olunur");
        return users.findById((Long) id).orElseThrow(() -> ApiException.unauthorized("Giriş tələb olunur"));
    }

    public Dto.MeRes me() {
        E.AppUser u = current();
        return new Dto.MeRes(u.id, u.email, u.name, u.role);
    }

    public void requireAdmin() {
        if (!ADMIN.equals(current().role)) throw ApiException.forbidden("Bu əməliyyat yalnız administrator üçündür");
    }

    @Transactional
    public void changePassword(String current, String neu) {
        E.AppUser u = current();
        if (current == null || !enc.matches(current, u.passwordHash)) throw ApiException.bad("Cari parol yanlışdır");
        u.passwordHash = enc.encode(checkPassword(neu));
        users.save(u);
    }

    // --- İstifadəçilərin idarəsi (yalnız ADMIN) ---

    private static Dto.UserRes res(E.AppUser u) { return new Dto.UserRes(u.id, u.email, u.name, u.role, u.active, u.createdAt); }

    private static String checkPassword(String p) {
        if (p == null || p.length() < 8) throw ApiException.bad("Parol ən azı 8 simvol olmalıdır");
        return p;
    }

    private static String checkRole(String r) {
        if (r == null || !ROLES.contains(r)) throw ApiException.bad("Rol yanlışdır (ADMIN və ya ACCOUNTANT)");
        return r;
    }

    public List<Dto.UserRes> listUsers() {
        requireAdmin();
        return users.findAllByOrderByIdAsc().stream().map(AuthService::res).toList();
    }

    @Transactional
    public Dto.UserRes createUser(Dto.UserReq r) {
        requireAdmin();
        String email = r.email() == null ? "" : r.email().trim().toLowerCase();
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw ApiException.bad("E-poçt yanlışdır");
        if (users.findByEmailIgnoreCase(email).isPresent()) throw ApiException.conflict("email_movcuddur", "Bu e-poçtla istifadəçi artıq var");
        E.AppUser u = new E.AppUser();
        u.email = email;
        u.name = r.name() == null ? "" : r.name().trim();
        u.role = checkRole(r.role() == null ? ACCOUNTANT : r.role());
        u.active = r.active() == null || r.active();
        u.passwordHash = enc.encode(checkPassword(r.password()));
        u.createdAt = LocalDateTime.now();
        return res(users.save(u));
    }

    /** E-poçt dəyişmir. Özünü passiv etmək / rolunu endirmək olmaz; ən azı bir aktiv ADMIN qalmalıdır */
    @Transactional
    public Dto.UserRes updateUser(Long id, Dto.UserReq r) {
        requireAdmin();
        E.AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("İstifadəçi tapılmadı"));
        boolean self = u.id.equals(current().id);
        String role = r.role() == null ? u.role : checkRole(r.role());
        boolean active = r.active() == null ? u.active : r.active();
        if (self && (!active || !ADMIN.equals(role))) throw ApiException.conflict("ozunu_deyisme", "Öz rolunuzu dəyişə və ya özünüzü passiv edə bilməzsiniz");
        if (ADMIN.equals(u.role) && u.active && (!active || !ADMIN.equals(role)) && users.countByRoleAndActiveTrue(ADMIN) <= 1)
            throw ApiException.conflict("son_admin", "Ən azı bir aktiv administrator qalmalıdır");
        if (r.name() != null) u.name = r.name().trim();
        u.role = role;
        u.active = active;
        boolean newPassword = r.password() != null && !r.password().isEmpty();
        if (newPassword) u.passwordHash = enc.encode(checkPassword(r.password()));
        users.save(u);
        // Passiv edilən və ya parolu sıfırlanan istifadəçinin sessiyaları bağlanır
        if (!self && (!active || newPassword)) sessions.deleteByUserId(u.id);
        return res(u);
    }

    @Transactional
    public void deleteUser(Long id) {
        requireAdmin();
        E.AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("İstifadəçi tapılmadı"));
        if (u.id.equals(current().id)) throw ApiException.conflict("ozunu_deyisme", "Özünüzü silə bilməzsiniz");
        if (ADMIN.equals(u.role) && u.active && users.countByRoleAndActiveTrue(ADMIN) <= 1)
            throw ApiException.conflict("son_admin", "Ən azı bir aktiv administrator qalmalıdır");
        sessions.deleteByUserId(u.id);
        users.delete(u);
    }
}
