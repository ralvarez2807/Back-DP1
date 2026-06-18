package com.tasf.b2b.application.usecase;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Gestiona la sesión única y permanente de "Operación Día a Día".
 *
 * <p>A diferencia de las simulaciones manuales (una por usuario, con fecha de inicio
 * y fin elegidas), la operación día a día es un proceso de servidor que corre en su
 * propio hilo desde el arranque, anclado a la fecha y hora reales de hoy, y refleja
 * los planes de vuelo recurrentes del día en tiempo real.</p>
 *
 * <p>Es un singleton lógico: aunque haya muchos clientes mirando el dashboard, todos
 * comparten la misma sesión. {@link #ensureRunning()} es idempotente y se auto-repara:
 * si la sesión terminó (alcanzó su horizonte) o fue detenida, la vuelve a crear.</p>
 *
 * <p>Internamente reutiliza por completo el motor de simulación: la sesión vive en el
 * mismo {@link SimulationRegistry}, por lo que los endpoints existentes
 * {@code /simulations/{id}/snapshot}, {@code /dashboard} y el WebSocket
 * {@code /simulations/{id}/ws} funcionan sin cambios para ella.</p>
 */
@Service
public class DailyOperationsService {

    private final RunSimulationUseCase runSimulationUseCase;
    private final SimulationRegistry   registry;

    /** Factor de velocidad. 1.0 = tiempo real estricto (hoy se vive a hora real). */
    private final double speedFactor;
    /** Horizonte en días hacia adelante para el fin de la corrida (rolling horizon acota la memoria). */
    private final int    horizonDays;

    public DailyOperationsService(RunSimulationUseCase runSimulationUseCase,
                                  SimulationRegistry registry,
                                  @Value("${app.daily-ops.speed-factor:1.0}") double speedFactor,
                                  @Value("${app.daily-ops.horizon-days:14}") int horizonDays) {
        this.runSimulationUseCase = runSimulationUseCase;
        this.registry             = registry;
        this.speedFactor          = speedFactor;
        this.horizonDays          = horizonDays;
    }

    /**
     * Devuelve la sesión día-a-día activa, creándola si no existe (o si la anterior
     * terminó). Sincronizado para que peticiones HTTP concurrentes y el arranque
     * automático no creen sesiones duplicadas.
     */
    public synchronized SimulationSession ensureRunning() {
        SimulationSession existing = registry.findByUser(RunSimulationUseCase.DAILY_OPS_USER);
        if (existing != null && isActive(existing)) {
            return existing;
        }
        // Limpiar un posible residuo terminado antes de relanzar.
        if (existing != null) {
            registry.remove(existing.getId());
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        Instant end = now.plus(horizonDays, ChronoUnit.DAYS);
        String id   = runSimulationUseCase.startDailyOperations(now, end, speedFactor);

        System.out.printf("[OPS] Operación Día a Día iniciada: %s (x%.1f, horizonte %dd)%n",
                id, speedFactor, horizonDays);
        return registry.findOrThrow(id);
    }

    /** Arranca el hilo de operación día a día al levantar la aplicación. Best-effort. */
    @EventListener(ApplicationReadyEvent.class)
    public void autoStartOnBoot() {
        try {
            ensureRunning();
        } catch (Exception e) {
            // No bloquear el arranque del servidor si, p. ej., aún no hay datos de
            // aeropuertos/vuelos cargados. El primer GET /operations lo reintentará.
            System.err.printf("[OPS] No se pudo auto-iniciar la operación día a día: %s%n", e.getMessage());
        }
    }

    private boolean isActive(SimulationSession s) {
        return switch (s.getStatus()) {
            case STARTING, RUNNING, PAUSED -> true;
            default -> false;
        };
    }
}
