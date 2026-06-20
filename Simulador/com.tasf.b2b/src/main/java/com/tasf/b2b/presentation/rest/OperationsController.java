package com.tasf.b2b.presentation.rest;

import com.tasf.b2b.application.port.in.InjectShipmentCommand;
import com.tasf.b2b.application.port.in.InjectShipmentResult;
import com.tasf.b2b.application.port.in.SimulationControlPort;
import com.tasf.b2b.application.usecase.DailyOperationsService;
import com.tasf.b2b.application.usecase.SimulationSession;
import com.tasf.b2b.domain.simulator.SimulationClock;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

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
    private final SimulationControlPort  controlPort;

    public OperationsController(DailyOperationsService dailyOps,
                               SimulationControlPort controlPort) {
        this.dailyOps    = dailyOps;
        this.controlPort = controlPort;
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

    // ── Carga manual de órdenes de maletas ─────────────────────────────────────

    /**
     * Cuerpo del POST /operations/orders.
     *
     * @param originIcao ICAO del aeropuerto de origen (requerido)
     * @param destIcao   ICAO del aeropuerto de destino (requerido)
     * @param quantity   cantidad de maletas (> 0)
     * @param clientId   identificador opcional del cliente/operario
     */
    record CreateOrderRequest(String originIcao, String destIcao, Integer quantity, String clientId) {}

    record CreateOrderResponse(String shipmentId, List<String> baggageIds, String originIcao,
                               String destIcao, int quantity, Instant entryTime) {}

    /**
     * Registra una orden de maletas en la Operación Día a Día con hora = ahora y la
     * enruta de inmediato a vuelos con almacenamiento disponible (vía el optimizador).
     * Crea la sesión día-a-día si aún no estaba corriendo.
     */
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateOrderResponse createOrder(@RequestBody CreateOrderRequest req, Principal principal) {
        if (req.destIcao() == null || req.destIcao().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campo requerido: destIcao");
        if (req.quantity() == null || req.quantity() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity debe ser un entero mayor que 0");

        // El origen es opcional en el body: para un operario de ciudad se IMPONE su sede
        // a partir del usuario logueado; el admin/usuario sin ciudad sí lo envía.
        String destIcao   = req.destIcao().trim().toUpperCase();
        String originIcao  = (req.originIcao() != null && !req.originIcao().isBlank())
                ? req.originIcao().trim().toUpperCase() : null;
        String username    = principal != null ? principal.getName() : null;
        String clientId    = (req.clientId() != null && !req.clientId().isBlank())
                ? req.clientId() : (username != null ? username : "OPERARIO");

        SimulationSession session = dailyOps.ensureRunning();

        InjectShipmentResult result = controlPort.injectShipment(
                session.getId(),
                new InjectShipmentCommand(originIcao, destIcao, req.quantity(), clientId, username));

        return new CreateOrderResponse(
                result.shipmentId(), result.baggageIds(), result.originIcao(),
                result.destIcao(), result.quantity(), result.entryTime());
    }
}
