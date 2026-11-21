package vn.haui.o2ofood.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.haui.o2ofood.common.TimeProvider;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;
    private final TimeProvider timeProvider;

    public JwtTokenProvider(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.access-expiration-ms:3600000}") long accessExpirationMs,
            @Value("${app.security.jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs,
            TimeProvider timeProvider) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
        this.timeProvider = timeProvider;
    }

    public String generateAccessToken(UserPrincipal principal) {
        Instant now = timeProvider.instantNow();
        Instant expiry = now.plusMillis(accessExpirationMs);
        return Jwts.builder()
                .subject(String.valueOf(principal.getId()))
                .claim("email", principal.getEmail())
                .claim("fullName", principal.getFullName())
                .claim("roles", principal.getRoleCodes())
                .claim("tokenType", "ACCESS")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(UserPrincipal principal) {
        Instant now = timeProvider.instantNow();
        Instant expiry = now.plusMillis(refreshExpirationMs);
        return Jwts.builder()
                .subject(String.valueOf(principal.getId()))
                .claim("email", principal.getEmail())
                .claim("tokenType", "REFRESH")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .clock(() -> Date.from(timeProvider.instantNow()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token, String expectedType) {
        try {
            Claims claims = parseClaims(token);
            String tokenType = claims.get("tokenType", String.class);
            return expectedType == null || expectedType.equals(tokenType);
        } catch (Exception ex) {
            return false;
        }
    }

    public UserPrincipal toPrincipal(String token) {
        Claims claims = parseClaims(token);
        Long userId = Long.parseLong(claims.getSubject());
        String email = claims.get("email", String.class);
        String fullName = claims.get("fullName", String.class);
        Object rawRoles = claims.get("roles");
        Set<String> roles = new HashSet<>();
        if (rawRoles instanceof Collection<?> col) {
            for (Object item : col) {
                if (item != null) {
                    roles.add(item.toString());
                }
            }
        } else {
            roles.addAll(List.of(SecurityConstants.ROLE_CUSTOMER));
        }
        return new UserPrincipal(userId, email, "", fullName, "ACTIVE", roles);
    }

    public long getAccessExpirationMs() {
        return accessExpirationMs;
    }
}
