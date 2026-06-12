package com.tasf.b2b.presentation.rest;

import com.tasf.b2b.infrastructure.security.JwtService;
import com.tasf.b2b.infrastructure.security.UserRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Autenticación: login y refresh de tokens JWT.
 *
 * POST /api/v1/auth/login   → valida credenciales, devuelve accessToken
 * POST /api/v1/auth/refresh → valida el token actual, devuelve uno nuevo con expiración renovada
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtService      jwtService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository  userRepository;

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    // Rate limiting: máx 5 intentos por IP por minuto en /login
    private final ConcurrentHashMap<String, Bucket> loginBuckets = new ConcurrentHashMap<>();

    public AuthController(JwtService jwtService, PasswordEncoder passwordEncoder, UserRepository userRepository) {
        this.jwtService      = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.userRepository  = userRepository;
    }

    // ── DTOs de request/response ──────────────────────────────────────────────

    record LoginRequest(String username, String passwordHash) {}
    record AuthResponse(String accessToken, Instant expiresAt) {}

    // ── Endpoints ─────────────────────────────────────────────────────────────

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest req, HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        log.info("LOGIN intent — ip={} username={} content-type={} content-length={}",
                ip, req.username(),
                httpRequest.getContentType(),
                httpRequest.getContentLengthLong());
        Bucket bucket = loginBuckets.computeIfAbsent(ip, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(5)
                                .refillGreedy(5, Duration.ofMinutes(1))
                                .build())
                        .build()
        );
        if (!bucket.tryConsume(1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos. Espera un momento.");
        }

        String hash = userRepository.findPasswordHash(req.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"));

        if (!passwordEncoder.matches(req.passwordHash(), hash)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        }

        String  token     = jwtService.generateToken(req.username());
        Instant expiresAt = jwtService.extractExpiration(token);
        return new AuthResponse(token, expiresAt);
    }

    /**
     * Renueva el token. El frontend llama a esto antes de que expire el token.
     * Requiere que el token actual sea válido (el filtro JWT lo valida antes de llegar aquí).
     */
    @PostMapping("/refresh")
    public AuthResponse refresh(@RequestHeader("Authorization") String authHeader) {
        String token    = authHeader.substring(7);
        String username = jwtService.extractUsername(token);

        String  newToken  = jwtService.generateToken(username);
        Instant expiresAt = jwtService.extractExpiration(newToken);
        return new AuthResponse(newToken, expiresAt);
    }
}
