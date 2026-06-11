package com.tasf.b2b.presentation.rest;

import com.tasf.b2b.application.dto.DashboardView;
import com.tasf.b2b.application.dto.SnapshotView;
import com.tasf.b2b.application.port.in.SimulationQueryPort;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * GET /api/v1/simulations/:id/dashboard
 *
 * Métricas agregadas de la sesión en tiempo real.
 * El frontend puede hacer polling cada 1-2 segundos para actualizar el dashboard.
 *
 * Todos los valores se calculan en el momento de la llamada desde el grafo en
 * memoria — no hay caché. Si hay alta frecuencia de consultas, se puede añadir
 * un caché de 500ms sin impactar la precisión perceptible.
 */
@RestController
@RequestMapping("/api/v1/simulations")
public class MonitoringController {

    private final SimulationQueryPort queryPort;

    public MonitoringController(SimulationQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    // ── DTO de respuesta ──────────────────────────────────────────────────────

    /**
     * delivered        — maletas que llegaron a su destino
     * pending          — maletas sin ruta (ALNS no ha encontrado vuelos aún)
     * assigned         — maletas con ruta asignada, esperando en aeropuerto
     * inFlight         — maletas actualmente en el aire
     * slaBreaches      — maletas cuyo deadline ya pasó (en cualquier estado)
     * throughputPerHour — entregadas / horas simuladas transcurridas
     */
    record DashboardResponse(
            Instant simTime,
            int     delivered,
            int     pending,
            int     assigned,
            int     inFlight,
            int     slaBreaches,
            double  throughputPerHour) {

        static DashboardResponse from(DashboardView v) {
            return new DashboardResponse(v.simTime(), v.delivered(), v.pending(),
                    v.assigned(), v.inFlight(), v.slaBreaches(), v.throughputPerHour());
        }
    }

    // ── Endpoint ──────────────────────────────────────────────────────────────

    @GetMapping("/{id}/dashboard")
    public DashboardResponse getDashboard(@PathVariable String id) {
        return DashboardResponse.from(queryPort.getDashboard(id));
    }

    // ── Snapshot ──────────────────────────────────────────────────────────────

    record FlightResponse(
            String  flightId,
            String  fromIcao,
            String  toIcao,
            Instant depTime,
            Instant arrTime,
            String  status,
            int     load,
            int     capacity) {

        static FlightResponse from(SnapshotView.FlightSnap s) {
            return new FlightResponse(s.flightId(), s.fromIcao(), s.toIcao(),
                    s.depTime(), s.arrTime(), s.status(), s.load(), s.capacity());
        }
    }

    record BaggageResponse(
            String  baggageId,
            String  status,
            String  currentIcao,
            String  flightId,
            String  destIcao,
            Instant deadlineUtc) {

        static BaggageResponse from(SnapshotView.BaggageSnap s) {
            return new BaggageResponse(s.baggageId(), s.status(), s.currentIcao(),
                    s.flightId(), s.destIcao(), s.deadlineUtc());
        }
    }

    record AirportResponse(
            String  icao,
            String  city,
            String  continent,
            int     load,
            int     pending,
            int     capacity) {

        static AirportResponse from(SnapshotView.AirportSnap s) {
            return new AirportResponse(s.icao(), s.city(), s.continent(),
                    s.load(), s.pending(), s.capacity());
        }
    }

    record SnapshotResponse(
            Instant                simTime,
            Instant                simStart,
            Instant                simEnd,
            String                 status,
            List<FlightResponse>   flights,
            List<BaggageResponse>  baggages,
            List<AirportResponse>  airports) {

        static SnapshotResponse from(SnapshotView v) {
            return new SnapshotResponse(
                    v.simTime(), v.simStart(), v.simEnd(), v.status(),
                    v.flights().stream().map(FlightResponse::from).toList(),
                    v.baggages().stream().map(BaggageResponse::from).toList(),
                    v.airports().stream().map(AirportResponse::from).toList());
        }
    }

    @GetMapping("/{id}/snapshot")
    public SnapshotResponse getSnapshot(@PathVariable String id) {
        return SnapshotResponse.from(queryPort.getSnapshot(id));
    }
}
