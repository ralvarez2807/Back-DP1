package com.tasf.b2b.presentation.rest;

import com.tasf.b2b.application.dto.FinishedSessionView;
import com.tasf.b2b.application.dto.SimSessionView;
import com.tasf.b2b.application.port.in.DisruptionCommand;
import com.tasf.b2b.application.port.in.SimulationControlPort;
import com.tasf.b2b.application.port.in.SimulationQueryPort;
import com.tasf.b2b.application.port.in.StartSimulationCommand;
import com.tasf.b2b.application.usecase.FinishedSessionCache;
import com.tasf.b2b.domain.simulator.SimulationConfig;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
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
    private final FinishedSessionCache  finishedSessionCache;

    public SimulationController(SimulationControlPort controlPort,
                                SimulationQueryPort   queryPort,
                                FinishedSessionCache  finishedSessionCache) {
        this.controlPort          = controlPort;
        this.queryPort            = queryPort;
        this.finishedSessionCache = finishedSessionCache;
    }

    // ── DTOs de request/response ──────────────────────────────────────────────

    /**
     * DB:     requiere simStart, simEnd, speedFactor.
     * MANUAL: simStart, simEnd, speedFactor deben ser null (se ignoran si se envían).
     */
    record CreateSessionRequest(
            String  dataSource,
            String  solverTimingMode,
            String  optimizerMode,
            Instant simStart,
            Instant simEnd,
            Double  speedFactor,
            Boolean collapseOnFailure
    ) {}

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
    public SessionResponse create(@RequestBody CreateSessionRequest req, Principal principal) {
        if (req.dataSource() == null)       throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo requerido: dataSource (DB | MANUAL)");
        if (req.solverTimingMode() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo requerido: solverTimingMode (REAL_TIME | PAUSE | EVENT_DRIVEN)");
        if (req.optimizerMode() == null)    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo requerido: optimizerMode (ALNS_ONLY | GENETIC_ONLY | ALNS_ACTIVE_GENETIC_EVAL | GENETIC_ACTIVE_ALNS_EVAL)");

        SimulationConfig.DataSource       ds  = parseEnum(SimulationConfig.DataSource.class,       req.dataSource(),       "dataSource");
        SimulationConfig.SolverTimingMode stm = parseEnum(SimulationConfig.SolverTimingMode.class, req.solverTimingMode(), "solverTimingMode");
        SimulationConfig.OptimizerMode    om  = parseEnum(SimulationConfig.OptimizerMode.class,    req.optimizerMode(),    "optimizerMode");

        if (ds == SimulationConfig.DataSource.DB) {
            if (req.simStart()    == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo requerido para DB: simStart");
            if (req.simEnd()      == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo requerido para DB: simEnd");
            if (req.speedFactor() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo requerido para DB: speedFactor");
            if (req.speedFactor() <= 0)    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "speedFactor debe ser mayor que 0");
        }

        StartSimulationCommand cmd = new StartSimulationCommand(principal.getName(), ds, stm, om,
                req.simStart(), req.simEnd(), req.speedFactor(),
                Boolean.TRUE.equals(req.collapseOnFailure()));

        String sessionId = controlPort.start(cmd);
        return SessionResponse.from(queryPort.getSession(sessionId));
    }

    /**
     * Devuelve la sesión activa del usuario autenticado, o 404 si no tiene ninguna.
     * Útil para reconectar desde otro navegador sin conocer el sessionId.
     */
    @GetMapping("/mine")
    public SessionResponse getMine(Principal principal) {
        SimSessionView view = queryPort.getSessionByUser(principal.getName());
        if (view == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay sesión activa para este usuario");
        return SessionResponse.from(view);
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String fieldName) {
        try {
            return Enum.valueOf(type, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Valor inválido para " + fieldName + ": '" + value + "'");
        }
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

    /**
     * Resultado final de una sesión que ya terminó (fin normal, colapso o stop):
     * status, razón del colapso si aplica, y la última ruta asignada por maleta.
     * Solo disponible hasta 3 minutos después de terminar — pasado ese tiempo,
     * o si el id nunca existió, o si la sesión sigue corriendo, responde 404.
     */
    @GetMapping("/{id}/result")
    public FinishedSessionView getResult(@PathVariable String id) {
        return finishedSessionCache.findOrThrow(id);
    }

    // ── Circunstancias (disrupciones) ─────────────────────────────────────────

    /**
     * Body del POST /simulations/:id/disruptions.
     * kind: CANCELLATION | AVERIA | SEGMENT_BLOCK | NODE_BLOCK
     *
     * CANCELLATION usa scheduleId (horario recurrente sin fecha, ORIG-DEST-HH:mm):
     * el backend resuelve si cancela la instancia de hoy o de mañana según la regla
     * de 1h simulada antes de la partida. AVERIA sigue usando flightId (con fecha).
     */
    record DisruptionRequest(String kind, String scheduleId, String flightId, String originIcao,
                             String destIcao, Instant fromUtc, Instant toUtc, int severity) {}

    record DisruptionResponse(String resolvedFlightId, int affectedFlights, List<String> flightIds) {}

    /**
     * Inyecta una circunstancia (cancelación / avería / bloqueo de tramo o nodo).
     * Cancela los vuelos afectados en la simulación y devuelve sus IDs para que el
     * frontend los represente en el mapa con el color/ideografía de cada tipo.
     */
    @PostMapping("/{id}/disruptions")
    public DisruptionResponse injectDisruption(@PathVariable String id,
                                               @RequestBody DisruptionRequest req) {
        DisruptionCommand.Kind kind = DisruptionCommand.Kind.valueOf(req.kind());
        if (kind == DisruptionCommand.Kind.CANCELLATION
                && (req.scheduleId() == null || req.scheduleId().isBlank())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Campo requerido: scheduleId");
        }

        DisruptionCommand cmd = new DisruptionCommand(
                kind, req.scheduleId(), req.flightId(), req.originIcao(), req.destIcao(),
                req.fromUtc(), req.toUtc(), req.severity());
        List<String> affected = controlPort.injectDisruption(id, cmd);
        String resolvedFlightId = kind == DisruptionCommand.Kind.CANCELLATION && !affected.isEmpty()
                ? affected.get(0) : null;
        return new DisruptionResponse(resolvedFlightId, affected.size(), affected);
    }

    // ── Disrupciones en lote (LE-70, LE-71: cancelación masiva) ───────────────

    record BulkDisruptionRequest(List<DisruptionRequest> disruptions) {}
    record BulkDisruptionResponse(List<DisruptionResponse> results) {}

    /**
     * Inyecta una lista de disrupciones en un solo request (cancelación masiva).
     * Sin generador aleatorio server-side: el operario elige manualmente cuáles
     * cancelar (el front puede armar la lista con una selección al azar si quiere).
     */
    @PostMapping("/{id}/disruptions/bulk")
    public BulkDisruptionResponse injectDisruptionsBulk(@PathVariable String id,
                                                        @RequestBody BulkDisruptionRequest req) {
        if (req.disruptions() == null || req.disruptions().isEmpty())
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Campo requerido: disruptions (no vacío)");

        List<DisruptionResponse> results = req.disruptions().stream()
                .map(r -> injectDisruption(id, r))
                .toList();
        return new BulkDisruptionResponse(results);
    }
}
