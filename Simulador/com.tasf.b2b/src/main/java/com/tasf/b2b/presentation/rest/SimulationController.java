package com.tasf.b2b.presentation.rest;

import com.tasf.b2b.application.dto.SimSessionView;
import com.tasf.b2b.application.port.in.DisruptionCommand;
import com.tasf.b2b.application.port.in.SimulationControlPort;
import com.tasf.b2b.application.port.in.SimulationQueryPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * Ciclo de vida de sesiones de simulación.
 *
 * POST   /api/v1/simulations              → crea e inicia una sesión
 * GET    /api/v1/simulations/:id          → estado actual de la sesión
 * POST   /api/v1/simulations/:id/pause    → pausa el reloj de simulación
 * POST   /api/v1/simulations/:id/resume   → reanuda el reloj
 * POST   /api/v1/simulations/:id/stop     → detiene todos los hilos y libera recursos
 *
 * El frontend envía simStart y simEnd; el backend calcula el speedFactor
 * automáticamente para que dure ~15 minutos de tiempo real.
 */
@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {

    private final SimulationControlPort controlPort;
    private final SimulationQueryPort   queryPort;

    public SimulationController(SimulationControlPort controlPort,
                                SimulationQueryPort   queryPort) {
        this.controlPort = controlPort;
        this.queryPort   = queryPort;
    }

    // ── DTOs de request/response ──────────────────────────────────────────────

    /**
     * Body del POST /simulations.
     * simStart y simEnd en formato ISO-8601 UTC: "2026-01-02T00:00:00Z"
     * Jackson convierte automáticamente el string JSON a Instant.
     */
    record CreateSessionRequest(Instant simStart, Instant simEnd) {}

    /**
     * Respuesta con el estado de una sesión.
     * simTime es el tiempo dentro de la simulación (no el reloj de pared).
     */
    record SessionResponse(String id, String status, Instant simTime,
                           Instant simStart, Instant simEnd) {
        static SessionResponse from(SimSessionView v) {
            return new SessionResponse(v.id(), v.status(), v.simTime(), v.simStart(), v.simEnd());
        }
    }

    // ── Endpoints ─────────────────────────────────────────────────────────────

    /**
     * Crea e inicia la simulación.
     * 201 Created — la sesión existe pero puede que todavía esté en "starting"
     * mientras los hilos arrancan.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse create(@RequestBody CreateSessionRequest req) {
        System.out.printf("[CTRL] POST /simulations recibido: %s → %s%n", req.simStart(), req.simEnd());
        String sessionId = controlPort.start(req.simStart(), req.simEnd());
        SessionResponse resp = SessionResponse.from(queryPort.getSession(sessionId));
        System.out.printf("[CTRL] POST /simulations respondiendo 201: id=%s status=%s%n", resp.id(), resp.status());
        return resp;
    }

    /**
     * Estado actual de la sesión: simTime, status, rango de fechas.
     * Útil para que el frontend muestre el reloj simulado en tiempo real.
     */
    @GetMapping("/{id}")
    public SessionResponse get(@PathVariable String id) {
        return SessionResponse.from(queryPort.getSession(id));
    }

    /**
     * Pausa el reloj. Los eventos de vuelo dejan de dispararse.
     * Lanza 409 si la sesión no está en estado "running".
     */
    @PostMapping("/{id}/pause")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pause(@PathVariable String id) {
        controlPort.pause(id);
    }

    /**
     * Reanuda desde donde se pausó. El tiempo pausado no cuenta en el reloj.
     * Lanza 409 si la sesión no está en estado "paused".
     */
    @PostMapping("/{id}/resume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resume(@PathVariable String id) {
        controlPort.resume(id);
    }

    /**
     * Detiene todos los hilos y elimina la sesión del registry.
     * No es reversible — hay que llamar POST /simulations para iniciar una nueva.
     */
    @PostMapping("/{id}/stop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stop(@PathVariable String id) {
        controlPort.stop(id);
    }

    // ── Circunstancias (disrupciones) ─────────────────────────────────────────

    /**
     * Body del POST /simulations/:id/disruptions.
     * kind: CANCELLATION | AVERIA | SEGMENT_BLOCK | NODE_BLOCK
     */
    record DisruptionRequest(String kind, String flightId, String originIcao,
                             String destIcao, Instant fromUtc, Instant toUtc, int severity) {}

    record DisruptionResponse(int affectedFlights, List<String> flightIds) {}

    /**
     * Inyecta una circunstancia (cancelación / avería / bloqueo de tramo o nodo).
     * Cancela los vuelos afectados en la simulación y devuelve sus IDs para que el
     * frontend los represente en el mapa con el color/ideografía de cada tipo.
     */
    @PostMapping("/{id}/disruptions")
    public DisruptionResponse injectDisruption(@PathVariable String id,
                                               @RequestBody DisruptionRequest req) {
        DisruptionCommand cmd = new DisruptionCommand(
                DisruptionCommand.Kind.valueOf(req.kind()),
                req.flightId(), req.originIcao(), req.destIcao(),
                req.fromUtc(), req.toUtc(), req.severity());
        List<String> affected = controlPort.injectDisruption(id, cmd);
        return new DisruptionResponse(affected.size(), affected);
    }
}
