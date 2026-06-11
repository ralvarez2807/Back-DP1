package com.tasf.b2b.application.usecase;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro central de sesiones de simulación activas.
 *
 * Permite que hasta dos sesiones corran simultáneamente (una con datos TXT,
 * otra con datos en tiempo real, por ejemplo). Cada sesión tiene su propio
 * grafo, reloj e hilos — no comparten estado mutable entre sí.
 *
 * Usamos ConcurrentHashMap porque las consultas al registry pueden llegar
 * desde hilos HTTP mientras el runner actualiza el status de la sesión.
 *
 * Cuando llegue Spring Boot, esta clase se anota con @Component y se convierte
 * en un singleton automáticamente. Por ahora es una clase normal que se
 * instancia una vez en la clase de configuración principal.
 */
public class SimulationRegistry {

    // sessionId (UUID) → sesión activa
    private final ConcurrentHashMap<String, SimulationSession> sessions =
            new ConcurrentHashMap<>();

    /** Registra una nueva sesión. Llamado justo antes de arrancar sus hilos. */
    public void register(SimulationSession session) {
        sessions.put(session.getId(), session);
    }

    /**
     * Busca una sesión por ID.
     * @throws IllegalArgumentException si no existe — el controlador REST
     *         convierte esto en HTTP 404.
     */
    public SimulationSession findOrThrow(String sessionId) {
        SimulationSession s = sessions.get(sessionId);
        if (s == null) {
            throw new IllegalArgumentException("Sesión no encontrada: " + sessionId);
        }
        return s;
    }

    /** Elimina una sesión del registro (después de stop). */
    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    /** Todas las sesiones activas — para monitoreo global o admin. */
    public Collection<SimulationSession> all() {
        return Collections.unmodifiableCollection(sessions.values());
    }
}
