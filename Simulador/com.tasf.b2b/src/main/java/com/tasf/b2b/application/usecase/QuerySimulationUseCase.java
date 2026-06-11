package com.tasf.b2b.application.usecase;

import com.tasf.b2b.application.dto.BaggageRouteView;
import com.tasf.b2b.application.dto.BaggageView;
import com.tasf.b2b.application.dto.DashboardView;
import com.tasf.b2b.application.dto.SimSessionView;
import com.tasf.b2b.application.dto.SnapshotView;
import com.tasf.b2b.domain.model.graph.componentsgraph.STEdge;
import com.tasf.b2b.domain.model.graph.componentsgraph.WaitEdge;
import com.tasf.b2b.application.port.in.SimulationQueryPort;
import com.tasf.b2b.domain.model.graph.SpaceTimeGraph;
import com.tasf.b2b.domain.model.graph.componentsgraph.FlightEdge;
import com.tasf.b2b.domain.model.graph.movable.Baggage;
import com.tasf.b2b.domain.simulator.SimulationClock;
import com.tasf.b2b.domain.simulator.SimulationRunner;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Consultas de estado sobre una sesión de simulación activa.
 *
 * Lee directamente del SpaceTimeGraph en memoria — no hay base de datos.
 * Los métodos son solo lectura: nunca modifican el grafo ni los baggages.
 *
 * NOTA DE THREAD SAFETY:
 * El SpaceTimeGraph solo lo escribe el hilo "simulation-runner". Si un hilo HTTP
 * llama getDashboard() mientras el runner está en medio de un handleFlightArrival(),
 * puede ver un estado parcialmente actualizado (ej: el baggage ya llegó pero los
 * contadores aún no se actualizaron). Esto es aceptable para un dashboard en tiempo
 * real: la inconsistencia dura microsegundos y el siguiente polling lo corrige.
 * Si se necesitara consistencia fuerte habría que sincronizar o usar Redis como
 * intermediario (que ya está planeado).
 */
public class QuerySimulationUseCase implements SimulationQueryPort {

    private final SimulationRegistry registry;

    public QuerySimulationUseCase(SimulationRegistry registry) {
        this.registry = registry;
    }

    // ── getSession ────────────────────────────────────────────────────────────

    @Override
    public SimSessionView getSession(String sessionId) {
        SimulationSession session = registry.findOrThrow(sessionId);
        SimulationRunner  runner  = session.getRunner();
        return new SimSessionView(
                session.getId(),
                session.getStatus().name().toLowerCase(),
                runner.getClock().now(),
                session.getConfig().simStart(),
                session.getConfig().simEnd());
    }

    // ── getDashboard ──────────────────────────────────────────────────────────

    @Override
    public DashboardView getDashboard(String sessionId) {
        SimulationSession session = registry.findOrThrow(sessionId);
        SimulationRunner  runner  = session.getRunner();
        SpaceTimeGraph    graph   = session.getGraph();
        SimulationClock   clock   = runner.getClock();
        Instant           simNow  = clock.now();

        // Hacer snapshots locales de las colecciones para que el cálculo
        // sea consistente aunque el runner modifique las colas mientras calculamos
        int delivered = runner.getDeliveredBaggages().size();
        int pending   = graph.getPendingBaggages().size();

        // Contar cuántos de los assigned están realmente en vuelo vs esperando
        // (currentEdge es FlightEdge → en vuelo; WaitEdge → esperando en aeropuerto)
        int inFlight = 0;
        for (Baggage b : new ArrayList<>(graph.getAssignedBaggages())) {
            if (b.getCurrentEdge() instanceof FlightEdge) inFlight++;
        }
        int assigned = graph.getAssignedBaggages().size() - inFlight;

        // SLA breaches: baggages cuyo deadline ya pasó en cualquier estado
        // Incluye pending, assigned, inFlight y delivered
        int slaBreaches = countSlaBreaches(runner, graph, simNow);

        // Throughput: entregados por hora simulada transcurrida desde el inicio
        double elapsedHours = Duration.between(session.getConfig().simStart(), simNow)
                .toSeconds() / 3600.0;
        double throughput = elapsedHours > 0 ? delivered / elapsedHours : 0.0;

        return new DashboardView(simNow, delivered, pending, assigned, inFlight,
                slaBreaches, Math.round(throughput * 10.0) / 10.0);
    }

    // ── getBaggageState ───────────────────────────────────────────────────────

    @Override
    public BaggageView getBaggageState(String sessionId, String baggageId) {
        SimulationSession session = registry.findOrThrow(sessionId);
        SimulationRunner  runner  = session.getRunner();
        Baggage b = findBaggage(session, baggageId);
        return toBaggageView(b, runner);
    }

    // ── getBaggageRoute ───────────────────────────────────────────────────────

    @Override
    public BaggageRouteView getBaggageRoute(String sessionId, String baggageId) {
        SimulationSession session = registry.findOrThrow(sessionId);
        SimulationRunner  runner  = session.getRunner();
        Baggage b = findBaggage(session, baggageId);

        boolean isDelivered = runner.getDeliveredBaggages().stream()
                .anyMatch(d -> d.getId().equals(b.getId()));

        STEdge current = b.getCurrentEdge();
        List<BaggageRouteView.Leg> legs = new ArrayList<>();

        // Tramos ya recorridos (escalas anteriores confirmadas)
        for (STEdge e : b.getRouteTraveled()) {
            if (e instanceof FlightEdge fe) legs.add(legOf(fe, "TRAVELED"));
        }
        // Tramo en curso + tramos planificados
        for (STEdge e : b.getExpectedRoute()) {
            if (e instanceof FlightEdge fe) {
                legs.add(legOf(fe, e.equals(current) ? "IN_FLIGHT" : "PLANNED"));
            }
        }

        String status;
        String currentIcao;
        if (isDelivered) {
            status = "DELIVERED";
            currentIcao = b.getDestIcao();
        } else if (current instanceof FlightEdge) {
            status = "IN_FLIGHT";
            currentIcao = b.getCurrentAirport();
        } else if (b.isUnassigned()) {
            status = "PENDING";
            currentIcao = b.getCurrentAirport();
        } else {
            status = "WAITING";
            currentIcao = b.getCurrentAirport();
        }

        return new BaggageRouteView(b.getId(), status, currentIcao,
                b.getDestIcao(), b.getDeadlineUtc(), legs);
    }

    private BaggageRouteView.Leg legOf(FlightEdge fe, String state) {
        return new BaggageRouteView.Leg(
                fe.getFromNode().getIcao(),
                fe.getToNode().getIcao(),
                fe.getFromNode().getTimeUtc(),
                fe.getToNode().getTimeUtc(),
                fe.getIdFlightEdge(),
                state);
    }

    // Busca la maleta en el grafo activo (pending + assigned) o en el histórico
    // de entregadas del runner. Lanza IllegalArgumentException si no existe.
    private Baggage findBaggage(SimulationSession session, String baggageId) {
        SimulationRunner runner = session.getRunner();
        SpaceTimeGraph   graph  = session.getGraph();
        Optional<Baggage> found = graph.getAllBaggages().stream()
                .filter(b -> b.getId().equals(baggageId))
                .findFirst();
        if (found.isEmpty()) {
            found = runner.getDeliveredBaggages().stream()
                    .filter(b -> b.getId().equals(baggageId))
                    .findFirst();
        }
        return found.orElseThrow(() ->
                new IllegalArgumentException("Baggage no encontrado: " + baggageId
                        + " en sesión " + session.getId()));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private BaggageView toBaggageView(Baggage b, SimulationRunner runner) {
        // Determinar si está entregado (solo posible si está en la lista del runner)
        boolean isDelivered = runner.getDeliveredBaggages().stream()
                .anyMatch(d -> d.getId().equals(b.getId()));

        if (isDelivered) {
            // Una vez entregado, currentAirport = destino
            return new BaggageView(b.getId(), "DELIVERED",
                    b.getDestIcao(), null, b.getDestIcao(), b.getDeadlineUtc());
        }

        // Derivar status desde el estado del currentEdge y expectedRoute
        String status;
        String flightId = null;

        if (b.getCurrentEdge() instanceof FlightEdge fe) {
            // Está en el aire: currentEdge es un FlightEdge activo
            status   = "IN_FLIGHT";
            flightId = fe.getIdFlightEdge();
        } else if (b.isUnassigned()) {
            // Sin ruta asignada: esperando que ALNS le encuentre un vuelo
            status = "PENDING";
        } else {
            // Tiene ruta asignada pero está en WaitEdge: esperando en aeropuerto
            status = "WAITING";
        }

        return new BaggageView(b.getId(), status,
                b.getCurrentAirport(), flightId, b.getDestIcao(), b.getDeadlineUtc());
    }

    // ── getSnapshot ───────────────────────────────────────────────────────────

    @Override
    public SnapshotView getSnapshot(String sessionId) {
        SimulationSession session = registry.findOrThrow(sessionId);
        SimulationRunner  runner  = session.getRunner();
        SpaceTimeGraph    graph   = session.getGraph();
        Instant           simNow  = runner.getClock().now();

        // Vuelos en el horizonte actual
        List<SnapshotView.FlightSnap> flights = new ArrayList<>();
        for (FlightEdge fe : graph.getAllFlightEdges()) {
            Instant dep = fe.getFromNode().getTimeUtc();
            Instant arr = fe.getToNode().getTimeUtc();
            String flightStatus;
            if (fe.isCancelled()) {
                flightStatus = "CANCELLED";
            } else if (dep.isAfter(simNow)) {
                flightStatus = "SCHEDULED";
            } else if (arr.isAfter(simNow)) {
                flightStatus = "DEPARTED";
            } else {
                flightStatus = "ARRIVED";
            }
            flights.add(new SnapshotView.FlightSnap(
                    fe.getIdFlightEdge(),
                    fe.getFromNode().getIcao(),
                    fe.getToNode().getIcao(),
                    dep, arr,
                    flightStatus,
                    fe.getLoad(),
                    fe.getCapacity()));
        }

        // Baggages activos
        List<SnapshotView.BaggageSnap> baggages = new ArrayList<>();
        for (Baggage b : new ArrayList<>(graph.getAllBaggages())) {
            baggages.add(toBaggageSnap(b, false));
        }
        for (Baggage b : runner.getDeliveredBaggages()) {
            baggages.add(toBaggageSnap(b, true));
        }

        // Ocupación de almacenes (aeropuertos) — calculada en el backend para que el
        // frontend muestre el semáforo de cada almacén coordinado con el estado real.
        // load = maletas que están físicamente esperando en el almacén (WAITING + PENDING).
        // Las maletas IN_FLIGHT viajan en un avión (no ocupan almacén); las DELIVERED ya salieron.
        Map<String, int[]> occByIcao = new HashMap<>(); // icao -> [load, pending]
        for (SnapshotView.BaggageSnap b : baggages) {
            boolean waiting = "WAITING".equals(b.status());
            boolean pending = "PENDING".equals(b.status());
            if ((waiting || pending) && b.currentIcao() != null) {
                int[] acc = occByIcao.computeIfAbsent(b.currentIcao(), k -> new int[2]);
                acc[0]++;                 // load
                if (pending) acc[1]++;    // pending
            }
        }

        List<SnapshotView.AirportSnap> airports = new ArrayList<>();
        for (var airport : graph.getAllAirports()) {
            int[] occ = occByIcao.getOrDefault(airport.getIcao(), new int[2]);
            airports.add(new SnapshotView.AirportSnap(
                    airport.getIcao(),
                    airport.getCity(),
                    airport.getContinent(),
                    occ[0],            // load
                    occ[1],            // pending
                    airport.getCapacity()));
        }

        return new SnapshotView(
                simNow,
                session.getConfig().simStart(),
                session.getConfig().simEnd(),
                session.getStatus().name().toLowerCase(),
                runner.getClock().getSpeedFactor(),
                flights,
                baggages,
                airports);
    }

    private SnapshotView.BaggageSnap toBaggageSnap(Baggage b, boolean delivered) {
        if (delivered) {
            return new SnapshotView.BaggageSnap(
                    b.getId(), "DELIVERED", b.getDestIcao(), null,
                    b.getDestIcao(), b.getDeadlineUtc());
        }
        String status;
        String flightId = null;
        if (b.getCurrentEdge() instanceof FlightEdge fe) {
            status   = "IN_FLIGHT";
            flightId = fe.getIdFlightEdge();
        } else if (b.isUnassigned()) {
            status = "PENDING";
        } else {
            status = "WAITING";
        }
        return new SnapshotView.BaggageSnap(
                b.getId(), status, b.getCurrentAirport(), flightId,
                b.getDestIcao(), b.getDeadlineUtc());
    }

    private int countSlaBreaches(SimulationRunner runner, SpaceTimeGraph graph, Instant simNow) {
        int breaches = 0;

        // Baggages activos (pending + assigned)
        for (Baggage b : new ArrayList<>(graph.getAllBaggages())) {
            if (b.getDeadlineUtc() != null && b.getDeadlineUtc().isBefore(simNow)) breaches++;
        }
        // Baggages entregados — aunque llegaron tarde también cuentan como breach
        for (Baggage b : runner.getDeliveredBaggages()) {
            if (b.getDeadlineUtc() != null && b.getDeadlineUtc().isBefore(simNow)) breaches++;
        }
        return breaches;
    }
}
