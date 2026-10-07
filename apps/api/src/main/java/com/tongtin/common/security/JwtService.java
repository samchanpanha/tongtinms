package com.tongtin.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String TOKEN_TYPE = "typ";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-ttl-minutes}") long accessTtlMinutes,
            @Value("${app.jwt.refresh-ttl-days}") long refreshTtlDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = Duration.ofMinutes(accessTtlMinutes);
        this.refreshTtl = Duration.ofDays(refreshTtlDays);
    }

    public String accessToken(Long userId, Long ownerId, List<String> roles) {
        return token(ACCESS, userId, ownerId, roles, accessTtl);
    }

    public String refreshToken(Long userId, Long ownerId, List<String> roles) {
        return token(REFRESH, userId, ownerId, roles, refreshTtl);
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    public AuthPrincipal parse(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!ACCESS.equals(claims.get(TOKEN_TYPE, String.class))) {
            throw new JwtException("Wrong token type");
        }
        Long userId = Long.valueOf(claims.getSubject());
        Long ownerId = claims.get("ownerId", Long.class);
        List<String> roles = claims.get("roles", List.class);
        return new AuthPrincipal(userId, ownerId, roles == null ? List.of() : roles);
    }

    public AuthPrincipal parseRefresh(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!REFRESH.equals(claims.get(TOKEN_TYPE, String.class))) {
            throw new JwtException("Wrong token type");
        }
        Long userId = Long.valueOf(claims.getSubject());
        Long ownerId = claims.get("ownerId", Long.class);
        List<String> roles = claims.get("roles", List.class);
        return new AuthPrincipal(userId, ownerId, roles == null ? List.of() : roles);
    }

    private String token(String type, Long userId, Long ownerId, List<String> roles, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("ownerId", ownerId)
                .claim("roles", roles)
                .claim(TOKEN_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }
}
