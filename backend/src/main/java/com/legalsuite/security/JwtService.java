package com.legalsuite.security;

import com.legalsuite.domain.AppUser;
import com.legalsuite.domain.Client;
import com.legalsuite.domain.Tenant;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final long accessMs;
    private final long refreshMs;

    public JwtService(
            @Value("${legalsuite.jwt.secret}") String secret,
            @Value("${legalsuite.jwt.access-ms:900000}") long accessMs,
            @Value("${legalsuite.jwt.refresh-ms:604800000}") long refreshMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessMs = accessMs;
        this.refreshMs = refreshMs;
    }

    public String accessToken(AppUser user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("tenantId", user.getTenantId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole())
                .claim("name", user.getFullName())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessMs))
                .signWith(key)
                .compact();
    }

    public String portalToken(Client client, Tenant tenant) {
        return Jwts.builder()
                .subject(client.getId().toString())
                .claim("tenantId", tenant.getId().toString())
                .claim("email", client.getEmail())
                .claim("role", "client")
                .claim("type", "portal")
                .claim("name", client.displayName())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessMs))
                .signWith(key)
                .compact();
    }

    public String refreshToken(AppUser user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("tenantId", user.getTenantId().toString())
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshMs))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public UUID userId(String token) {
        return UUID.fromString(parse(token).getSubject());
    }

    public long getAccessMs() {
        return accessMs;
    }
}
