package com.tasf.b2b.application.usecase;

import com.tasf.b2b.application.dto.FinishedSessionView;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda en memoria el resultado final de las sesiones que ya terminaron
 * (fin normal, colapso o stop manual), el tiempo justo para que el front
 * pueda consultarlo tras reconectar. No hay persistencia en BD: pasado el
 * TTL, el resultado se descarta solo.
 *
 * Purga perezosa (al guardar uno nuevo y al leer uno vencido) — no hace
 * falta un hilo de limpieza aparte para un TTL tan corto.
 */
public class FinishedSessionCache {

    private static final Duration TTL = Duration.ofMinutes(3);

    private record Entry(FinishedSessionView view, Instant expiresAt) {}

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public void record(FinishedSessionView view) {
        purgeExpired();
        entries.put(view.id(), new Entry(view, Instant.now().plus(TTL)));
    }

    /**
     * @throws IllegalArgumentException si no existe o ya expiró → HTTP 404
     *         (mismo patrón que {@link SimulationRegistry#findOrThrow}).
     */
    public FinishedSessionView findOrThrow(String sessionId) {
        Entry e = entries.get(sessionId);
        if (e == null || Instant.now().isAfter(e.expiresAt())) {
            entries.remove(sessionId);
            throw new IllegalArgumentException("Resultado no encontrado: " + sessionId);
        }
        return e.view();
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        entries.values().removeIf(e -> now.isAfter(e.expiresAt()));
    }
}
