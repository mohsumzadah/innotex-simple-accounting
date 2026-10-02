package az.innotex.sade;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** /api/** üçün Bearer token yoxlaması (giriş istisnadır) */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuthService auth;

    public WebConfig(AuthService auth) { this.auth = auth; }

    public static String bearer(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        return h != null && h.startsWith("Bearer ") ? h.substring(7).trim() : null;
    }

    @Override
    public void addInterceptors(InterceptorRegistry reg) {
        reg.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
                if ("OPTIONS".equals(req.getMethod())) return true;
                if (!auth.authenticate(bearer(req))) throw ApiException.unauthorized("Giriş tələb olunur");
                return true;
            }
        }).addPathPatterns("/api/**").excludePathPatterns("/api/auth/login");
    }
}
