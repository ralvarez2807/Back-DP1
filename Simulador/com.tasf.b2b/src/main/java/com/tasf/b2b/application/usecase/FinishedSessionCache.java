package com.tasf.b2b.application.usecase;

import com.tasf.b2b.application.dto.FinishedSessionView;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda en memoria la última solución conocida de cada sesión: se actualiza
 * periódicamente mientras la sesión corre (ver RunSimulationUseCase, hilo
 * "solution-snapshot-*") y con el resultado definitivo cuando termina (fin
 * normal, colapso o stop manual). Así, si la ejecución se corta de forma
 * abrupta (crash, kill) sin pasar por un cierre prolijo, la última
 * planificación exitosa igual queda disponible por API. No hay persistencia
 * en BD: pasado el TTL, el resultado indexado por sessionId se descarta solo.
 *
 * Purga perezosa (al guardar uno nuevo y al leer uno vencido) — no hace
 * falta un hilo de limpieza aparte para un TTL tan corto.
 *
 * Además de esa vista con TTL por sessionId, se mantiene por separado la
 * última vista de cada cuenta (username) sin expiración: sobrevive a los
 * 5 minutos y solo se reemplaza cuando esa misma cuenta corre otra
 * simulación. El campo {@code id} de la vista deja anotado a qué sessionId
 * pertenece.
 */
public class FinishedSessionCache {

    private static final Duration TTL = Duration.ofMinutes(5);

    private record Entry(FinishedSessionView view, Instant expiresAt) {}

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, FinishedSessionView> lastByAccount = new ConcurrentHashMap<>();

    public void record(FinishedSessionView view) {
        purgeExpired();
        entries.put(view.id(), new Entry(view, Instant.now().plus(TTL)));
        lastByAccount.put(view.username(), view);
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

    /**
     * Última vista cacheada para la sesión, si existe y no venció — no arroja.
     * Usado al colapsar: permite conservar las rutas del último snapshot bueno
     * (anterior a la falla) en vez de recalcular con el estado que ya colapsó.
     */
    public Optional<FinishedSessionView> peek(String sessionId) {
        Entry e = entries.get(sessionId);
        if (e == null || Instant.now().isAfter(e.expiresAt())) return Optional.empty();
        return Optional.of(e.view());
    }

    /**
     * Última vista conocida de la cuenta, sin TTL — se sobreescribe solo cuando
     * esa cuenta vuelve a correr una simulación (ver {@link #record}).
     *
     * @throws IllegalArgumentException si la cuenta nunca corrió una simulación → HTTP 404
     */
    public FinishedSessionView findByAccountOrThrow(String username) {
        FinishedSessionView view = lastByAccount.get(username);
        if (view == null) throw new IllegalArgumentException("Resultado no encontrado para la cuenta: " + username);
        return view;
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        entries.values().removeIf(e -> now.isAfter(e.expiresAt()));
    }
}
