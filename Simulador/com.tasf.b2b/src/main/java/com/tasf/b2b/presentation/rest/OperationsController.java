package com.tasf.b2b.presentation.rest;

import com.tasf.b2b.application.usecase.DailyOperationsService;
import com.tasf.b2b.application.usecase.SimulationSession;
import com.tasf.b2b.domain.simulator.SimulationClock;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Operación Día a Día — vista operativa en vivo anclada a la fecha real de hoy.
 *
 * <p>GET /api/v1/operations devuelve el identificador y estado de la sesión
 * día-a-día (creándola si hace falta). El frontend usa ese {@code id} para
 * consumir los endpoints ya existentes de la sesión:</p>
 *
 * <ul>
 *   <li>GET  /api/v1/simulations/{id}/snapshot  — estado completo (vuelos, aeropuertos, maletas)</li>
 *   <li>GET  /api/v1/simulations/{id}/dashboard — métricas agregadas</li>
 *   <li>WS   /api/v1/simulations/{id}/ws        — stream de eventos en vivo</li>
 * </ul>
 *
 * <p>De esta forma el módulo reutiliza toda la maquinaria de simulación sin duplicar
 * DTOs ni lógica de consulta.</p>
 */
@RestController
@RequestMapping("/api/v1/operations")
public class OperationsController {

    private final DailyOperationsService dailyOps;

    public OperationsController(DailyOperationsService dailyOps) {
        this.dailyOps = dailyOps;
    }

    /**
     * @param id          UUID de la sesión día-a-día (usar en /simulations/{id}/...)
     * @param status      starting | running | paused | completed | stopped
     * @param simTime     instante simulado actual (≈ ahora con speedFactor=1)
     * @param simStart    instante de arranque de la operación
     * @param simEnd      fin del horizonte de la operación
     * @param speedFactor factor de velocidad (1.0 = tiempo real)
     */
    record OperationsStatus(String id, String status, Instant simTime,
                            Instant simStart, Instant simEnd, double speedFactor) {}

    @GetMapping
    public OperationsStatus get() {
        SimulationSession session = dailyOps.ensureRunning();
        SimulationClock   clock   = session.getRunner().getClock();
        return new OperationsStatus(
                session.getId(),
                session.getStatus().name().toLowerCase(),
                clock.now(),
                session.getConfig().simStart(),
                session.getConfig().simEnd(),
                clock.getSpeedFactor());
    }
}
