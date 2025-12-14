package com.squirret.squirretbackend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {
    
    @Value("${jwt.secret}")
    private String secretKey;
    
    @Value("${jwt.expiration}")
    private long jwtExpiration;
    
    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;
    
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }
    
    public UUID extractUserId(String token) {
        String userIdStr = extractClaim(token, claims -> claims.get("userId", String.class));
        return UUID.fromString(userIdStr);
    }
    
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }
    
    public String generateToken(UUID userId, String email) {
        return generateToken(new HashMap<>(), userId, email);
    }
    
    public String generateToken(Map<String, Object> extraClaims, UUID userId, String email) {
        return buildToken(extraClaims, userId, email, jwtExpiration);
    }
    
    public String generateRefreshToken(UUID userId, String email) {
        return buildToken(new HashMap<>(), userId, email, refreshExpiration);
    }
    
    private String buildToken(Map<String, Object> extraClaims, UUID userId, String email, long expiration) {
        return Jwts
                .builder()
                .setClaims(extraClaims)
                .setSubject(email)
                .claim("userId", userId.toString())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * FastAPI 웹소켓용 단기 JWT 토큰 생성 (aud=inference-ws, 짧은 만료 시간)
     */
    public String generateInferenceWsToken(String sessionId, long expirationMillis) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("aud", "inference-ws");
        claims.put("sessionId", sessionId);
        return Jwts
                .builder()
                .setClaims(claims)
                .setSubject(sessionId)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }
    
    public boolean isTokenValid(String token, String email) {
        final String username = extractUsername(token);
        return (username.equals(email)) && !isTokenExpired(token);
    }
    
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
    
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
    
    private Claims extractAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    
    private SecretKey getSignInKey() {
        byte[] keyBytes = secretKey.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
