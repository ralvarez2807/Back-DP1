package com.tasf.b2b.presentation.rest;

import com.tasf.b2b.application.dto.BaggageRouteView;
import com.tasf.b2b.application.dto.BaggageView;
import com.tasf.b2b.application.port.in.SimulationQueryPort;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * GET /api/v1/simulations/:id/baggage/:baggageId
 *
 * Tracking de una maleta individual dentro de una sesión.
 * Devuelve el estado actual derivado del SpaceTimeGraph en memoria.
 *
 * Posibles estados:
 *   PENDING   — sin ruta; ALNS todavía no le asignó vuelos
 *   WAITING   — tiene ruta asignada, esperando en aeropuerto
 *   IN_FLIGHT — actualmente en el aire; flightId indica qué vuelo la lleva
 *   DELIVERED — llegó a su destino
 */
@RestController
@RequestMapping("/api/v1/simulations")
public class TrackingController {

    private final SimulationQueryPort queryPort;

    public TrackingController(SimulationQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    // ── DTO de respuesta ──────────────────────────────────────────────────────

    /**
     * currentIcao — aeropuerto donde está ahora
     *               si IN_FLIGHT: aeropuerto de salida del tramo en curso
     * flightId    — null salvo si está IN_FLIGHT
     */
    record BaggageResponse(
            String  baggageId,
            String  status,
            String  currentIcao,
            String  flightId,
            String  destIcao,
            Instant deadlineUtc) {

        static BaggageResponse from(BaggageView v) {
            return new BaggageResponse(v.baggageId(), v.status(), v.currentIcao(),
                    v.flightId(), v.destIcao(), v.deadlineUtc());
        }
    }

    // ── Endpoint ──────────────────────────────────────────────────────────────

    /**
     * @param id        UUID de la sesión de simulación
     * @param baggageId ID de la maleta (formato "S1-B3")
     */
    @GetMapping("/{id}/baggage/{baggageId}")
    public BaggageResponse getBaggage(@PathVariable String id,
                                      @PathVariable String baggageId) {
        return BaggageResponse.from(queryPort.getBaggageState(id, baggageId));
    }

    // ── Ruta de la maleta (escalas recorridas + en curso + planificadas) ──────

    record LegResponse(
            String  fromIcao,
            String  toIcao,
            Instant depTime,
            Instant arrTime,
            String  flightId,
            String  state) {       // TRAVELED | IN_FLIGHT | PLANNED
        static LegResponse from(BaggageRouteView.Leg l) {
            return new LegResponse(l.fromIcao(), l.toIcao(), l.depTime(),
                    l.arrTime(), l.flightId(), l.state());
        }
    }

    record BaggageRouteResponse(
            String            baggageId,
            String            status,
            String            currentIcao,
            String            destIcao,
            Instant           deadlineUtc,
            List<LegResponse> legs) {
        static BaggageRouteResponse from(BaggageRouteView v) {
            return new BaggageRouteResponse(v.baggageId(), v.status(), v.currentIcao(),
                    v.destIcao(), v.deadlineUtc(),
                    v.legs().stream().map(LegResponse::from).toList());
        }
    }

    /**
     * Ruta completa de una maleta para dibujarla en el mapa con sus escalas.
     * GET /api/v1/simulations/:id/baggage/:baggageId/route
     */
    @GetMapping("/{id}/baggage/{baggageId}/route")
    public BaggageRouteResponse getBaggageRoute(@PathVariable String id,
                                                @PathVariable String baggageId) {
        return BaggageRouteResponse.from(queryPort.getBaggageRoute(id, baggageId));
    }
}
