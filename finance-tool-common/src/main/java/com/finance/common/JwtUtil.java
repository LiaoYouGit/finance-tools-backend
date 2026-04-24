package com.finance.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final long refreshTokenRememberExpiration;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
                   @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration,
                   @Value("${jwt.refresh-token-remember-expiration}") long refreshTokenRememberExpiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.refreshTokenRememberExpiration = refreshTokenRememberExpiration;
    }

    public String generateAccessToken(Long userId, String account) {
        return buildToken(userId, account, accessTokenExpiration);
    }

    public String generateRefreshToken(Long userId, String account, boolean remember) {
        long expiration = remember ? refreshTokenRememberExpiration : refreshTokenExpiration;
        return buildToken(userId, account, expiration);
    }

    private String buildToken(Long userId, String account, long expiration) {
        return Jwts.builder()
                .claims(Map.of("userId", userId, "account", account))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }
}
