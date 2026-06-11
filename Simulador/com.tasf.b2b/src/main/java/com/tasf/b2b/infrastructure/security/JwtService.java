package com.tasf.b2b.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/**
 * Servicio que genera y valida JSON Web Tokens (JWT).
 *
 * ¿Qué es un JWT?
 * Un JWT tiene tres partes separadas por puntos: header.payload.signature
 *   header  → algoritmo de firma (HS256)
 *   payload → datos (subject=username, iat=emitido, exp=expira)
 *   signature → HMAC-SHA256(header + "." + payload, secretKey)
 *
 * El servidor firma el token al crearlo. Al recibir un request, verifica la firma.
 * Si la firma es válida y el token no expiró → el usuario está autenticado.
 * No hay estado en el servidor (stateless) — el token se valida sin consultar BD.
 *
 * Algoritmo usado: HS256 (HMAC-SHA256). Requiere clave secreta de ≥256 bits.
 */
@Component
public class JwtService {

    private final SecretKey secretKey;
    private final long      expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String base64Secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        // Decodifica el string Base64 y lo convierte en una clave HMAC-SHA256
        this.secretKey    = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.expirationMs = expirationMs;
    }

    /**
     * Genera un token JWT para el usuario dado.
     * @return string en formato "header.payload.signature"
     */
    public String generateToken(String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationMs))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Extrae el username (subject) de un token.
     * Lanza JwtException si el token es inválido o expiró.
     */
    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * Calcula cuándo expira el token (para incluirlo en la respuesta al frontend).
     */
    public Instant extractExpiration(String token) {
        return extractClaims(token).getExpiration().toInstant();
    }

    /**
     * Verifica que el token sea válido (firma correcta, no expirado) y
     * que pertenezca al username esperado.
     */
    public boolean isValid(String token, String expectedUsername) {
        try {
            String subject = extractUsername(token);
            return expectedUsername.equals(subject);
        } catch (Exception e) {
            return false; // firma inválida, token expirado, formato incorrecto, etc.
        }
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
