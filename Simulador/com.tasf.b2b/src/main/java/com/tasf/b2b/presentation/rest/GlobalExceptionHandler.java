package com.tasf.b2b.presentation.rest;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manejador global de excepciones para toda la API REST.
 *
 * Mapa de excepciones → HTTP status:
 *   HttpMessageNotReadableException  → 400 Bad Request  (body inválido o ausente)
 *   MethodArgumentNotValidException  → 400 Bad Request  (validación @Valid)
 *   ResponseStatusException          → el status que lleva la excepción (401, 409, 429, etc.)
 *   IllegalArgumentException         → 404 Not Found    (sesión/baggage no existe)
 *   IllegalStateException            → 409 Conflict     (pausar una sesión ya pausada, etc.)
 *   Exception                        → 500 Internal     (cualquier error no esperado)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNoResource(NoResourceFoundException e, HttpServletRequest req) {
        log.warn("404 {} {}: endpoint no encontrado", req.getMethod(), req.getRequestURI());
        return Map.of("error", "Endpoint no encontrado: " + e.getResourcePath());
    }

    /** GET con Content-Type: application/json y body vacío, body malformado, proxy que consume el stream, etc. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleUnreadableBody(HttpMessageNotReadableException e, HttpServletRequest req) {
        Throwable cause = e.getCause();
        String causeDetail = cause != null
                ? cause.getClass().getSimpleName() + ": " + cause.getMessage()
                : e.getMessage();
        log.warn("400 {} {}: cuerpo ilegible — {}", req.getMethod(), req.getRequestURI(), causeDetail);
        return Map.of("error", "Cuerpo de la solicitud inválido o ausente");
    }

    /** Errores de validación con @Valid / @Validated en el body */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(MethodArgumentNotValidException e, HttpServletRequest req) {
        String details = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("400 {} {}: validación fallida — {}", req.getMethod(), req.getRequestURI(), details);
        return Map.of("error", "Datos inválidos: " + details);
    }

    /** Captura explícitamente ResponseStatusException para respetar su status code (401, 429, etc.) */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e, HttpServletRequest req) {
        String message = e.getReason() != null ? e.getReason() : e.getMessage();
        log.warn("{} {} {}: {}", e.getStatusCode().value(), req.getMethod(), req.getRequestURI(), message);
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(IllegalArgumentException e, HttpServletRequest req) {
        log.warn("404 {} {}: {}", req.getMethod(), req.getRequestURI(), e.getMessage());
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleConflict(IllegalStateException e, HttpServletRequest req) {
        log.warn("409 {} {}: {}", req.getMethod(), req.getRequestURI(), e.getMessage());
        return Map.of("error", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleGeneral(Exception e, HttpServletRequest req) {
        String errorId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.error("[{}] 500 {} {}: {} — {}",
                errorId, req.getMethod(), req.getRequestURI(),
                e.getClass().getSimpleName(), e.getMessage(), e);
        return Map.of("error", "Error interno del servidor. Referencia: " + errorId);
    }
}
