package com.quhealthy.auth_service.service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration:86400000}") // Default 24 horas
    private long jwtExpiration;

    // =================================================================
    // 1. GENERACIÓN DE TOKENS (Crear)
    // =================================================================

    // Generar Token COMPLETO (Login exitoso)
    public String generateToken(Long userId, String email, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", userId);
        claims.put("email", email); // Guardamos email en claims custom
        claims.put("role", role);
        
        // IMPORTANTE: Pasamos el email como "Subject" para que Spring Security lo entienda
        return buildToken(claims, email, jwtExpiration); 
    }

    // Generar Token PARCIAL (Para 2FA - 5 minutos)
    public String generatePartialToken(Long userId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", userId);
        claims.put("role", role);
        claims.put("mfa", "pending");
        
        long partialExpiration = 1000 * 60 * 5; // 5 Minutos
        // Para el token parcial, usamos el ID como subject temporal
        return buildToken(claims, String.valueOf(userId), partialExpiration);
    }

    // Método privado constructor de JWT
    private String buildToken(Map<String, Object> extraClaims, String subject, long expiration) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(subject) // Establece el dueño del token (Standard JWT 'sub')
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // =================================================================
    // 2. VALIDACIÓN Y EXTRACCIÓN (Leer) - ¡ESTO ES LO NUEVO!
    // =================================================================

    // Extraer el "Username" (en nuestro caso, el email) del token
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // Extraer cualquier dato genérico del token
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // Validar si el token pertenece al usuario y no ha expirado
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        // Verifica que el email del token coincida con el usuario de la BD y que no esté vencido
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    // Verificar si ya caducó
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // Obtener fecha de expiración
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // Parsear el Token (Abrir la caja fuerte con la llave secreta)
    private Claims extractAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // Obtener la llave criptográfica
    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}