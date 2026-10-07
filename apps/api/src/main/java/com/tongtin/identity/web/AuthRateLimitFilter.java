package com.tongtin.identity.web;

import com.tongtin.settings.service.SettingsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final int defaultRegisterLimit;
    private final int defaultLoginLimit;
    private final SettingsService settingsService;
    private final Map<String, Window> registerWindows = new ConcurrentHashMap<>();
    private final Map<String, Window> loginWindows = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(
            @Value("${app.rate-limit.register-per-hour}") int registerLimit,
            @Value("${app.rate-limit.login-per-15min}") int loginLimit,
            SettingsService settingsService) {
        this.defaultRegisterLimit = registerLimit;
        this.defaultLoginLimit = loginLimit;
        this.settingsService = settingsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if ("POST".equals(request.getMethod())) {
            if (path.equals("/api/v1/auth/register-owner")
                    && !allow(registerWindows, clientIp(request),
                            settingsService.getInt("rate_limit_register_per_hour", defaultRegisterLimit),
                            Duration.ofHours(1))) {
                reject(response, "rate limit exceeded for register");
                return;
            }
            if (path.equals("/api/v1/auth/login")
                    && !allow(loginWindows, clientIp(request),
                            settingsService.getInt("rate_limit_login_per_15min", defaultLoginLimit),
                            Duration.ofMinutes(15))) {
                reject(response, "rate limit exceeded for login");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean allow(Map<String, Window> windows, String ip, int limit, Duration windowTtl) {
        Instant now = Instant.now();
        Window window = windows.compute(ip, (key, existing) ->
                existing == null || existing.expiresAt.isBefore(now) ? new Window(now.plus(windowTtl)) : existing);
        synchronized (window) {
            if (window.expiresAt.isBefore(now)) {
                window.expiresAt = now.plus(windowTtl);
                window.count.set(0);
            }
            return window.count.incrementAndGet() <= limit;
        }
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":429,\"message\":\"" + message + "\"}");
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Window {
        private volatile Instant expiresAt;
        private final AtomicInteger count = new AtomicInteger();

        private Window(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}
