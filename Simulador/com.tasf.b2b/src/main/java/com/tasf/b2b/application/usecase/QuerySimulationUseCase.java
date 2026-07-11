package com.tasf.b2b.application.usecase;

import com.tasf.b2b.application.dto.BaggageHistoryView;
import com.tasf.b2b.application.dto.BaggageRouteView;
import com.tasf.b2b.application.dto.BaggageView;
import com.tasf.b2b.application.dto.DashboardView;
import com.tasf.b2b.application.dto.AirportLiveView;
import com.tasf.b2b.application.dto.FlightView;
import com.tasf.b2b.application.dto.ReportView;
import com.tasf.b2b.application.dto.ShipmentDiagnosticsView;
import com.tasf.b2b.application.dto.ShipmentView;
import com.tasf.b2b.application.dto.SimSessionView;
import com.tasf.b2b.application.dto.SnapshotView;
import com.tasf.b2b.domain.model.graph.componentsgraph.STEdge;
import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;
import com.tasf.b2b.domain.model.graph.componentsgraph.WaitEdge;
import com.tasf.b2b.application.port.in.SimulationQueryPort;
import com.tasf.b2b.domain.model.graph.SpaceTimeGraph;
import com.tasf.b2b.domain.model.graph.componentsgraph.FlightEdge;
import com.tasf.b2b.domain.model.graph.movable.Baggage;
import com.tasf.b2b.domain.optimizer.alns.AlnsProjection;
import com.tasf.b2b.domain.optimizer.alns.AlnsProjectionBuilder;
import com.tasf.b2b.domain.optimizer.alns.BaggageState;
import com.tasf.b2b.domain.optimizer.alns.FlightSnapshot;
import com.tasf.b2b.domain.optimizer.alns.RouteFinder;
import com.tasf.b2b.domain.simulator.SimulationClock;
import com.tasf.b2b.domain.simulator.SimulationRunner;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Override
    public SimSessionView getSessionByUser(String username) {
        SimulationSession session = registry.findByUser(username);
        if (session == null) return null;
        SimulationRunner runner = session.getRunner();
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

        // Indicadores globales (LE-101, LE-102): se recalculan en cada llamada
        // (sin caché), lo que ya satisface "a lo más cada 4 min" — nunca están más
        // desactualizados que el momento exacto de la consulta.
        int fleetLoad = 0, fleetCapacity = 0;
        for (FlightEdge fe : graph.getAllFlightEdges()) {
            if (fe.isCancelled()) continue;
            fleetLoad     += fe.getLoad();
            fleetCapacity += fe.getCapacity();
        }
        double fleetOccupancyPct = fleetCapacity > 0 ? 100.0 * fleetLoad / fleetCapacity : 0.0;

        int airportLoad = pending + assigned; // baggages físicamente en un aeropuerto ahora
        int airportCapacity = graph.getAllAirports().stream().mapToInt(AirportDataDTO::getCapacity).sum();
        double airportOccupancyPct = airportCapacity > 0 ? 100.0 * airportLoad / airportCapacity : 0.0;

        return new DashboardView(simNow, delivered, pending, assigned, inFlight,
                slaBreaches, Math.round(throughput * 10.0) / 10.0,
                Math.round(fleetOccupancyPct * 10.0) / 10.0,
                Math.round(airportOccupancyPct * 10.0) / 10.0);
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
        return toRouteView(runner, b);
    }

    /**
     * "Última solución": la ruta asignada (o el estado sin ruta) de cada maleta
     * de la sesión. Recibe la SimulationSession directamente (no el id) para poder
     * llamarse justo antes de que la sesión salga del registry, sin depender de
     * que siga registrada.
     */
    public List<BaggageRouteView> buildAllRoutes(SimulationSession session) {
        SimulationRunner runner = session.getRunner();
        SpaceTimeGraph   graph  = session.getGraph();
        List<Baggage> all = new ArrayList<>();
        all.addAll(graph.getPendingBaggages());
        all.addAll(graph.getAssignedBaggages());
        all.addAll(runner.getDeliveredBaggages());
        return all.stream().map(b -> toRouteView(runner, b)).toList();
    }

    private BaggageRouteView toRouteView(SimulationRunner runner, Baggage b) {
        boolean isDelivered = runner.getDeliveredBaggages().stream()
                .anyMatch(d -> d.getId().equals(b.getId()));

        STEdge current = b.getCurrentEdge();
        List<BaggageRouteView.Leg> legs = new ArrayList<>();

        // Tramos ya recorridos (escalas anteriores confirmadas)
        for (STEdge e : b.getRouteTraveled()) {
            if (e instanceof FlightEdge fe) legs.add(legOf(fe, "ARRIVED"));
        }
        // Tramo en curso + tramos planificados
        for (STEdge e : b.getExpectedRoute()) {
            if (e instanceof FlightEdge fe) {
                legs.add(legOf(fe, e.equals(current) ? "DEPARTED" : "PLANNED"));
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

    // ── getBaggageHistory (LE-45) ─────────────────────────────────────────────

    @Override
    public BaggageHistoryView getBaggageHistory(String sessionId, String baggageId) {
        SimulationSession session = registry.findOrThrow(sessionId);
        Baggage b = findBaggage(session, baggageId);
        List<BaggageHistoryView.Entry> entries = b.getHistory().stream()
                .map(h -> new BaggageHistoryView.Entry(h.timestamp(), h.status(), h.icao(), h.flightId()))
                .toList();
        return new BaggageHistoryView(b.getId(), entries);
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

        List<SnapshotView.BaggageSnap> baggages = new ArrayList<>();
        List<Baggage> activeSnap = new ArrayList<>();
        activeSnap.addAll(new ArrayList<>(graph.getPendingBaggages()));
        activeSnap.addAll(new ArrayList<>(graph.getAssignedBaggages()));
        for (Baggage b : activeSnap) {
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

    // ── getFlights ────────────────────────────────────────────────────────────

    @Override
    public List<FlightView> getFlights(String sessionId) {
        SimulationSession session = registry.findOrThrow(sessionId);
        Instant simNow = session.getRunner().getClock().now();
        List<FlightView> result = new ArrayList<>();
        for (FlightEdge fe : session.getGraph().getAllFlightEdges()) {
            result.add(toFlightView(fe, simNow));
        }
        return result;
    }

    // ── getFlightDetail ───────────────────────────────────────────────────────

    @Override
    public FlightView.DetailView getFlightDetail(String sessionId, String flightId) {
        SimulationSession session = registry.findOrThrow(sessionId);
        Instant simNow = session.getRunner().getClock().now();
        SpaceTimeGraph graph = session.getGraph();

        FlightEdge target = graph.getAllFlightEdges().stream()
                .filter(fe -> fe.getIdFlightEdge().equals(flightId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Vuelo no encontrado en el horizonte: " + flightId));

        // Agrupar baggages asignados a este vuelo por shipmentId.
        // Incluye: currentEdge == target (IN_FLIGHT) y primer hop de expectedRoute == target (WAITING).
        Map<String, List<FlightView.BaggageOnFlight>> byShipment = new LinkedHashMap<>();
        Map<String, String[]> shipmentOriginDest = new LinkedHashMap<>();

        List<Baggage> activeForFlight = new ArrayList<>();
        activeForFlight.addAll(new ArrayList<>(graph.getPendingBaggages()));
        activeForFlight.addAll(new ArrayList<>(graph.getAssignedBaggages()));
        for (Baggage b : activeForFlight) {
            boolean onFlight = false;

            if (b.getCurrentEdge() instanceof FlightEdge fe
                    && fe.getIdFlightEdge().equals(flightId)) {
                onFlight = true;
            } else {
                List<STEdge> route = b.getExpectedRoute();
                if (!route.isEmpty() && route.get(0) instanceof FlightEdge fe
                        && fe.getIdFlightEdge().equals(flightId)) {
                    onFlight = true;
                }
            }

            if (onFlight) {
                String shipmentId = b.getShipment().getShipmentData().getId();
                byShipment.computeIfAbsent(shipmentId, k -> new ArrayList<>())
                        .add(new FlightView.BaggageOnFlight(
                                b.getId(), b.getDestIcao(), b.getDeadlineUtc()));
                shipmentOriginDest.computeIfAbsent(shipmentId, k -> new String[]{
                        b.getShipment().getShipmentData().getOriginAirport().getIcao(),
                        b.getDestIcao()
                });
            }
        }

        List<FlightView.ShipmentOnFlight> shipments = byShipment.entrySet().stream()
                .map(e -> {
                    String[] od = shipmentOriginDest.get(e.getKey());
                    return new FlightView.ShipmentOnFlight(
                            e.getKey(), od[0], od[1], e.getValue().size(), e.getValue());
                })
                .toList();

        return new FlightView.DetailView(
                target.getIdFlightEdge(),
                target.getFromNode().getIcao(),
                target.getToNode().getIcao(),
                target.getFromNode().getTimeUtc(),
                target.getToNode().getTimeUtc(),
                flightStatus(target, simNow),
                target.getLoad(),
                target.getCapacity(),
                FlightView.calcPct(target.getLoad(), target.getCapacity()),
                FlightView.calcLevel(target.getLoad(), target.getCapacity()),
                shipments);
    }

    // ── getShipments ──────────────────────────────────────────────────────────

    @Override
    public List<ShipmentView> getShipments(String sessionId) {
        SimulationSession session = registry.findOrThrow(sessionId);
        SimulationRunner  runner  = session.getRunner();
        SpaceTimeGraph    graph   = session.getGraph();
        int     pickupMinutes = runner.getConfig().pickupMinutes();
        Instant simNow        = runner.getClock().now();

        Map<String, List<Baggage>> activeByShipment    = new LinkedHashMap<>();
        Map<String, List<Baggage>> deliveredByShipment = new LinkedHashMap<>();

        // pending + assigned son disjuntos con deliveredBaggages; allBaggages NO lo es
        List<Baggage> activeBaggages = new ArrayList<>();
        activeBaggages.addAll(new ArrayList<>(graph.getPendingBaggages()));
        activeBaggages.addAll(new ArrayList<>(graph.getAssignedBaggages()));

        for (Baggage b : activeBaggages) {
            activeByShipment
                    .computeIfAbsent(b.getShipment().getShipmentData().getId(), k -> new ArrayList<>())
                    .add(b);
        }
        for (Baggage b : runner.getDeliveredBaggages()) {
            deliveredByShipment
                    .computeIfAbsent(b.getShipment().getShipmentData().getId(), k -> new ArrayList<>())
                    .add(b);
        }

        Set<String> allIds = new LinkedHashSet<>();
        allIds.addAll(activeByShipment.keySet());
        allIds.addAll(deliveredByShipment.keySet());

        List<ShipmentView> result = new ArrayList<>();
        for (String sid : allIds) {
            List<Baggage> active    = activeByShipment.getOrDefault(sid, List.of());
            List<Baggage> delivered = deliveredByShipment.getOrDefault(sid, List.of());
            Baggage sample = active.isEmpty() ? delivered.get(0) : active.get(0);

            int noRoute = 0, onTime = 0, late = 0, breached = 0;
            for (Baggage b : active) {
                // Vencida sin entregar: sigue activa (no entregada) y su deadline ya pasó.
                // Es el proxy en vivo de "no entregada"; al fin de la simulación son los fallos.
                if (b.getDeadlineUtc() != null && b.getDeadlineUtc().isBefore(simNow)) breached++;

                if (b.isRouteComplete()) {
                    // Entrega planificada = llegada del último tramo + recogida.
                    Instant delivery = b.getExpectedDestEdge().getToNode().getTimeUtc()
                            .plus(Duration.ofMinutes(pickupMinutes));
                    if (!delivery.isAfter(b.getDeadlineUtc())) onTime++;
                    else late++;
                } else {
                    noRoute++;
                }
            }

            result.add(new ShipmentView(
                    sid,
                    sample.getShipment().getShipmentData().getOriginAirport().getIcao(),
                    sample.getDestIcao(),
                    sample.getDeadlineUtc(),
                    active.size() + delivered.size(),
                    delivered.size(),
                    noRoute, onTime, late, breached));
        }
        return result;
    }

    // ── getShipmentDetail ─────────────────────────────────────────────────────

    @Override
    public ShipmentView.DetailView getShipmentDetail(String sessionId, String shipmentId) {
        SimulationSession session = registry.findOrThrow(sessionId);
        SimulationRunner  runner  = session.getRunner();
        SpaceTimeGraph    graph   = session.getGraph();

        List<Baggage> allActiveForDetail = new ArrayList<>();
        allActiveForDetail.addAll(new ArrayList<>(graph.getPendingBaggages()));
        allActiveForDetail.addAll(new ArrayList<>(graph.getAssignedBaggages()));
        List<Baggage> active = allActiveForDetail.stream()
                .filter(b -> b.getShipment().getShipmentData().getId().equals(shipmentId))
                .collect(Collectors.toList());

        List<Baggage> delivered = runner.getDeliveredBaggages().stream()
                .filter(b -> b.getShipment().getShipmentData().getId().equals(shipmentId))
                .collect(Collectors.toList());

        List<Baggage> all = new ArrayList<>(active);
        all.addAll(delivered);

        if (all.isEmpty())
            throw new IllegalArgumentException("Envío no encontrado: " + shipmentId);

        Set<String> deliveredIds = delivered.stream()
                .map(Baggage::getId).collect(Collectors.toSet());

        int pickupMinutes = runner.getConfig().pickupMinutes();
        Baggage sample = all.get(0);
        List<ShipmentView.BaggageDetail> details = all.stream()
                .map(b -> toBaggageDetail(b, deliveredIds.contains(b.getId()), pickupMinutes))
                .collect(Collectors.toList());

        return new ShipmentView.DetailView(
                shipmentId,
                sample.getShipment().getShipmentData().getOriginAirport().getIcao(),
                sample.getDestIcao(),
                sample.getDeadlineUtc(),
                all.size(),
                details);
    }

    private ShipmentView.BaggageDetail toBaggageDetail(Baggage b, boolean isDelivered, int pickupMinutes) {
        if (isDelivered) {
            return new ShipmentView.BaggageDetail(
                    b.getId(), "DELIVERED", b.getDestIcao(), null, null, List.of());
        }

        String status;
        if (b.getCurrentEdge() instanceof FlightEdge) {
            status = "IN_FLIGHT";
        } else if (b.isUnassigned()) {
            status = "PENDING";
        } else {
            status = "WAITING";
        }

        Instant eta    = null;
        Boolean onTime = null;
        if (b.isRouteComplete()) {
            // eta expuesta = llegada del último tramo; el cumplimiento de SLA se mide
            // sobre la entrega real (llegada + recogida) para coincidir con el dashboard.
            eta    = b.getExpectedDestEdge().getToNode().getTimeUtc();
            Instant delivery = eta.plus(Duration.ofMinutes(pickupMinutes));
            onTime = !delivery.isAfter(b.getDeadlineUtc());
        }

        // Tramos recorridos
        List<ShipmentView.Leg> legs = new ArrayList<>();
        for (STEdge e : b.getRouteTraveled()) {
            if (e instanceof FlightEdge fe) {
                legs.add(new ShipmentView.Leg(
                        fe.getFromNode().getIcao(), fe.getToNode().getIcao(),
                        fe.getFromNode().getTimeUtc(), fe.getToNode().getTimeUtc(),
                        "ARRIVED"));
            }
        }
        // Tramos planificados (el primero puede ser DEPARTED si currentEdge es FlightEdge)
        STEdge current = b.getCurrentEdge();
        for (STEdge e : b.getExpectedRoute()) {
            if (e instanceof FlightEdge fe) {
                legs.add(new ShipmentView.Leg(
                        fe.getFromNode().getIcao(), fe.getToNode().getIcao(),
                        fe.getFromNode().getTimeUtc(), fe.getToNode().getTimeUtc(),
                        e == current ? "DEPARTED" : "PLANNED"));
            }
        }

        return new ShipmentView.BaggageDetail(
                b.getId(), status, b.getCurrentAirport(), eta, onTime, legs);
    }

    // ── getShipmentDiagnostics ────────────────────────────────────────────────

    @Override
    public ShipmentDiagnosticsView getShipmentDiagnostics(String sessionId, String shipmentId) {
        SimulationSession session = registry.findOrThrow(sessionId);
        SimulationRunner  runner  = session.getRunner();
        SpaceTimeGraph    graph   = session.getGraph();
        Instant simNow         = runner.getClock().now();
        int     pickupMinutes  = runner.getConfig().pickupMinutes();
        int     connectMinutes = runner.getConfig().minConnectionMinutes();

        List<Baggage> active = new ArrayList<>();
        active.addAll(new ArrayList<>(graph.getPendingBaggages()));
        active.addAll(new ArrayList<>(graph.getAssignedBaggages()));
        active = active.stream()
                .filter(b -> b.getShipment().getShipmentData().getId().equals(shipmentId))
                .collect(Collectors.toList());
        List<Baggage> delivered = runner.getDeliveredBaggages().stream()
                .filter(b -> b.getShipment().getShipmentData().getId().equals(shipmentId))
                .collect(Collectors.toList());

        if (active.isEmpty() && delivered.isEmpty())
            throw new IllegalArgumentException("Envío no encontrado: " + shipmentId);

        Baggage sample    = active.isEmpty() ? delivered.get(0) : active.get(0);
        String  originIcao = sample.getShipment().getShipmentData().getOriginAirport().getIcao();
        String  destIcao   = sample.getDestIcao();
        Instant deadline   = sample.getDeadlineUtc();

        // Mismo snapshot del grafo que ve el ALNS en cada ciclo.
        AlnsProjection projection = AlnsProjectionBuilder.build(graph, simNow, connectMinutes, pickupMinutes);

        List<ShipmentDiagnosticsView.BaggageDiagnostic> diags = new ArrayList<>();

        // Entregadas pero tarde
        for (Baggage b : delivered) {
            Instant deliveredAt = actualDeliveryInstant(b, pickupMinutes);
            if (deliveredAt != null && b.getDeadlineUtc() != null && deliveredAt.isAfter(b.getDeadlineUtc())) {
                long lateMin = Duration.between(b.getDeadlineUtc(), deliveredAt).toMinutes();
                diags.add(new ShipmentDiagnosticsView.BaggageDiagnostic(
                        b.getId(), "DELIVERED", b.getDestIcao(), deliveredAt,
                        Duration.between(simNow, b.getDeadlineUtc()).toMinutes(),
                        true, false, deliveredAt, lateMin, b.getRouteTraveled().size(),
                        ShipmentDiagnosticsView.VERDICT_DELIVERED_LATE,
                        "Entregada " + lateMin + " min tarde.", List.of()));
            }
        }

        // Activas problemáticas: sin ruta completa o ya vencidas
        for (Baggage b : active) {
            boolean past = b.getDeadlineUtc() != null && b.getDeadlineUtc().isBefore(simNow);
            if (b.isRouteComplete() && !past) continue;

            String  status; String currentIcao; Instant availableFrom;
            if (b.getCurrentEdge() instanceof FlightEdge fe) {
                status = "IN_FLIGHT";
                currentIcao   = fe.getToNode().getIcao();
                availableFrom = fe.getToNode().getTimeUtc().isAfter(simNow) ? fe.getToNode().getTimeUtc() : simNow;
            } else if (b.isUnassigned()) {
                status = "PENDING"; currentIcao = b.getCurrentAirport(); availableFrom = simNow;
            } else {
                status = "WAITING"; currentIcao = b.getCurrentAirport(); availableFrom = simNow;
            }

            BaggageState bs = new BaggageState(b.getId(), currentIcao, availableFrom, destIcao, b.getDeadlineUtc());
            List<FlightSnapshot> best = RouteFinder.findFastestIgnoringDeadline(bs, projection);

            boolean reachable; Instant bestArr; long bestLate; int hops; String verdict; String expl;
            if (best.isEmpty()) {
                reachable = false; bestArr = null; bestLate = 0; hops = 0;
                verdict = ShipmentDiagnosticsView.VERDICT_NO_CONNECTIVITY;
                expl = "No existe NINGUNA cadena de vuelos desde " + currentIcao + " hacia " + destIcao
                     + " en el horizonte (ni ignorando el deadline). Falta conectividad o capacidad.";
            } else {
                bestArr  = best.get(best.size() - 1).arrTime();
                hops     = best.size();
                reachable = b.getDeadlineUtc() == null || !bestArr.isAfter(b.getDeadlineUtc());
                bestLate  = b.getDeadlineUtc() == null ? 0 : Duration.between(b.getDeadlineUtc(), bestArr).toMinutes();
                if (!reachable) {
                    verdict = ShipmentDiagnosticsView.VERDICT_DEADLINE_INFEASIBLE;
                    expl = "La ruta más rápida posible (" + hops + " vuelo[s]) llega " + bestLate
                         + " min DESPUÉS del deadline. Es físicamente imposible cumplir el SLA desde aquí; "
                         + "el fallo ocurrió antes (se posicionó tarde o perdió una conexión).";
                } else if (!b.isRouteComplete()) {
                    verdict = ShipmentDiagnosticsView.VERDICT_PLANNER_MISS;
                    expl = "SÍ existe una ruta que llega a tiempo (" + hops + " vuelo[s], holgura "
                         + (-bestLate) + " min) pero quedó SIN RUTA. Revisar el optimizador.";
                } else {
                    verdict = ShipmentDiagnosticsView.VERDICT_ON_TRACK;
                    expl = "Tiene ruta que llega a tiempo; aparece como vencida solo porque su deadline pasó estando en tránsito.";
                }
            }

            List<ShipmentDiagnosticsView.DirectFlightInfo> directs = new ArrayList<>();
            var byTime = projection.flightsByOrigin().get(currentIcao);
            if (byTime != null) {
                for (var bucket : byTime.values()) {
                    for (FlightSnapshot f : bucket) {
                        if (!f.toIcao().equals(destIcao)) continue;
                        boolean depOk = !f.depTime().isBefore(availableFrom.plusSeconds(pickupMinutes * 60L));
                        boolean arrOk = b.getDeadlineUtc() == null || !f.arrTime().isAfter(b.getDeadlineUtc());
                        boolean capOk = f.remainingCapacity() > 0;
                        boolean usable = depOk && arrOk && capOk;
                        String reason = usable ? "Usable"
                                : !depOk ? "Sale antes de que la maleta esté lista"
                                : !capOk ? "Sin capacidad"
                                : "Llega después del deadline";
                        directs.add(new ShipmentDiagnosticsView.DirectFlightInfo(
                                f.flightId(), f.depTime(), f.arrTime(), f.remainingCapacity(), usable, reason));
                    }
                }
            }

            diags.add(new ShipmentDiagnosticsView.BaggageDiagnostic(
                    b.getId(), status, currentIcao, availableFrom,
                    b.getDeadlineUtc() == null ? 0 : Duration.between(simNow, b.getDeadlineUtc()).toMinutes(),
                    b.isRouteComplete(), reachable, bestArr, bestLate, hops, verdict, expl, directs));
        }

        return new ShipmentDiagnosticsView(shipmentId, originIcao, destIcao, deadline, simNow, diags);
    }

    // ── getAirports ───────────────────────────────────────────────────────────

    @Override
    public List<AirportLiveView> getAirports(String sessionId) {
        SimulationSession session = registry.findOrThrow(sessionId);
        SpaceTimeGraph    graph   = session.getGraph();

        Map<String, Integer> loadByIcao = new HashMap<>();
        for (Baggage b : new ArrayList<>(graph.getAllBaggages())) {
            if (b.getCurrentEdge() instanceof WaitEdge we) {
                loadByIcao.merge(we.getFromNode().getIcao(), 1, Integer::sum);
            }
        }
        // Maletas que esperan recojo en su destino final: siguen ocupando almacén
        // hasta que el pasajero las recoge (ventana de pickupMinutes).
        for (SimulationRunner.PickupWaiting pw : session.getRunner().getAwaitingPickup()) {
            loadByIcao.merge(pw.icao(), 1, Integer::sum);
        }

        List<AirportLiveView> result = new ArrayList<>();
        for (var a : graph.getAllAirports()) {
            int load = loadByIcao.getOrDefault(a.getIcao(), 0);
            result.add(new AirportLiveView(
                    a.getIcao(), a.getCity(), a.getContinent(),
                    load, a.getCapacity(),
                    FlightView.calcPct(load, a.getCapacity()),
                    FlightView.calcLevel(load, a.getCapacity())));
        }
        return result;
    }

    // ── getAirportInbound ─────────────────────────────────────────────────────

    @Override
    public AirportLiveView.InboundView getAirportInbound(String sessionId, String icao) {
        SimulationSession session = registry.findOrThrow(sessionId);
        SpaceTimeGraph    graph   = session.getGraph();
        Instant           simNow  = session.getRunner().getClock().now();

        requireAirport(graph, icao);

        // Para cada baggage asignado, busca el primer FlightEdge que llega a icao en el futuro
        Map<String, FlightEdge>    edgeByFlight  = new LinkedHashMap<>();
        Map<String, List<Baggage>> baggByFlight  = new LinkedHashMap<>();

        for (Baggage b : new ArrayList<>(graph.getAssignedBaggages())) {
            for (STEdge e : b.getExpectedRoute()) {
                if (e instanceof FlightEdge fe
                        && fe.getToNode().getIcao().equals(icao)
                        && fe.getToNode().getTimeUtc().isAfter(simNow)) {
                    String fid = fe.getIdFlightEdge();
                    edgeByFlight.put(fid, fe);
                    baggByFlight.computeIfAbsent(fid, k -> new ArrayList<>()).add(b);
                    break;
                }
            }
        }

        List<AirportLiveView.InboundFlight> inbound = baggByFlight.entrySet().stream()
                .map(e -> {
                    FlightEdge fe = edgeByFlight.get(e.getKey());
                    List<String> shipmentIds = e.getValue().stream()
                            .map(b -> b.getShipment().getShipmentData().getId())
                            .distinct().toList();
                    List<AirportLiveView.InboundBaggage> bags = e.getValue().stream()
                            .map(b -> new AirportLiveView.InboundBaggage(
                                    b.getId(),
                                    b.getShipment().getShipmentData().getId(),
                                    b.getDestIcao()))
                            .toList();
                    return new AirportLiveView.InboundFlight(
                            e.getKey(), fe.getFromNode().getIcao(),
                            fe.getToNode().getTimeUtc(), e.getValue().size(), shipmentIds, bags);
                })
                .toList();

        return new AirportLiveView.InboundView(icao, simNow, inbound);
    }

    // ── getAirportOutbound ────────────────────────────────────────────────────

    @Override
    public AirportLiveView.OutboundView getAirportOutbound(String sessionId, String icao) {
        SimulationSession session = registry.findOrThrow(sessionId);
        SpaceTimeGraph    graph   = session.getGraph();
        Instant           simNow  = session.getRunner().getClock().now();

        requireAirport(graph, icao);

        Map<String, FlightEdge>    edgeByFlight = new LinkedHashMap<>();
        Map<String, List<Baggage>> baggByFlight = new LinkedHashMap<>();

        for (Baggage b : new ArrayList<>(graph.getAssignedBaggages())) {
            for (STEdge e : b.getExpectedRoute()) {
                if (e instanceof FlightEdge fe
                        && fe.getFromNode().getIcao().equals(icao)
                        && fe.getFromNode().getTimeUtc().isAfter(simNow)) {
                    String fid = fe.getIdFlightEdge();
                    edgeByFlight.put(fid, fe);
                    baggByFlight.computeIfAbsent(fid, k -> new ArrayList<>()).add(b);
                    break;
                }
            }
        }

        List<AirportLiveView.OutboundFlight> outbound = baggByFlight.entrySet().stream()
                .map(e -> {
                    FlightEdge fe = edgeByFlight.get(e.getKey());
                    List<String> shipmentIds = e.getValue().stream()
                            .map(b -> b.getShipment().getShipmentData().getId())
                            .distinct().toList();
                    return new AirportLiveView.OutboundFlight(
                            e.getKey(), fe.getToNode().getIcao(),
                            fe.getFromNode().getTimeUtc(), e.getValue().size(), shipmentIds);
                })
                .toList();

        return new AirportLiveView.OutboundView(icao, simNow, outbound);
    }

    // ── getAirportTransit ─────────────────────────────────────────────────────

    @Override
    public AirportLiveView.TransitView getAirportTransit(String sessionId, String icao) {
        SimulationSession session = registry.findOrThrow(sessionId);
        SimulationRunner  runner  = session.getRunner();
        SpaceTimeGraph    graph   = session.getGraph();
        Instant           simNow  = runner.getClock().now();
        int               connectMinutes = runner.getConfig().minConnectionMinutes();

        requireAirport(graph, icao);

        List<AirportLiveView.TransitBaggage> transit = new ArrayList<>();
        for (Baggage b : new ArrayList<>(graph.getAllBaggages())) {
            if (!(b.getCurrentEdge() instanceof WaitEdge we)) continue;
            if (!we.getFromNode().getIcao().equals(icao)) continue;

            String  nextFlightId = null;
            Instant nextDepTime  = null;
            List<STEdge> route = b.getExpectedRoute();
            if (!route.isEmpty() && route.get(0) instanceof FlightEdge fe) {
                nextFlightId = fe.getIdFlightEdge();
                nextDepTime  = fe.getFromNode().getTimeUtc();
            }

            // Momento en que la maleta llegó (o fue registrada) en este almacén:
            // última entrada de historial cuyo icao coincide con el aeropuerto actual.
            // Tras aterrizar, el runner registra un WAITING/PENDING en el ICAO de destino,
            // así que su timestamp es la llegada real al almacén.
            Instant arrivedAt = null;
            for (Baggage.StatusEntry h : b.getHistory()) {
                if (icao.equals(h.icao())) arrivedAt = h.timestamp();
            }
            // Fin de la ventana de conexión (procesamiento en almacén) — antes de esto
            // la maleta está "recién llegada" y aún no puede embarcarse.
            Instant readyAt = arrivedAt != null
                    ? arrivedAt.plus(Duration.ofMinutes(connectMinutes))
                    : null;

            transit.add(new AirportLiveView.TransitBaggage(
                    b.getId(),
                    b.getShipment().getShipmentData().getId(),
                    b.getDestIcao(),
                    b.getDeadlineUtc(),
                    nextFlightId,
                    nextDepTime,
                    arrivedAt,
                    readyAt,
                    false,   // no espera recojo: está en tránsito/conexión
                    null));
        }

        // Maletas que llegaron a su destino final y esperan recojo del pasajero en este
        // almacén (ocupan hasta el pickup). No están en ningún WaitEdge, se listan aparte.
        for (SimulationRunner.PickupWaiting pw : runner.getAwaitingPickup()) {
            if (!pw.icao().equals(icao)) continue;
            Baggage b = pw.baggage();
            transit.add(new AirportLiveView.TransitBaggage(
                    b.getId(),
                    b.getShipment().getShipmentData().getId(),
                    b.getDestIcao(),
                    b.getDeadlineUtc(),
                    null,               // no tiene próximo vuelo — ya llegó a destino
                    null,
                    pw.pickupAt().minus(Duration.ofMinutes(runner.getConfig().pickupMinutes())), // arrivedAt
                    pw.pickupAt(),      // readyAt = instante de recojo
                    true,               // espera recojo
                    pw.pickupAt()));
        }

        return new AirportLiveView.TransitView(icao, simNow, transit);
    }

    // ── getReport ─────────────────────────────────────────────────────────────

    @Override
    public ReportView getReport(String sessionId) {
        SimulationSession session = registry.findOrThrow(sessionId);
        SimulationRunner  runner  = session.getRunner();
        SpaceTimeGraph    graph   = session.getGraph();
        Instant           simNow  = runner.getClock().now();

        List<Baggage> active = new ArrayList<>();
        active.addAll(new ArrayList<>(graph.getPendingBaggages()));
        active.addAll(new ArrayList<>(graph.getAssignedBaggages()));
        List<Baggage> delivered = runner.getDeliveredBaggages();

        int inFlight = 0;
        for (Baggage b : active) {
            if (b.getCurrentEdge() instanceof FlightEdge) inFlight++;
        }
        int pending = graph.getPendingBaggages().size();

        // Shipments distintos del total de maletas
        Set<String> shipmentIds = new LinkedHashSet<>();
        for (Baggage b : active)    shipmentIds.add(b.getShipment().getShipmentData().getId());
        for (Baggage b : delivered) shipmentIds.add(b.getShipment().getShipmentData().getId());

        // Rutas más usadas: contar pares ICAO de routeTraveled
        Map<String, Integer> routeCount = new HashMap<>();
        for (Baggage b : active) {
            for (STEdge e : b.getRouteTraveled()) {
                if (e instanceof FlightEdge fe) {
                    String key = fe.getFromNode().getIcao() + "→" + fe.getToNode().getIcao();
                    routeCount.merge(key, 1, Integer::sum);
                }
            }
        }
        for (Baggage b : delivered) {
            for (STEdge e : b.getRouteTraveled()) {
                if (e instanceof FlightEdge fe) {
                    String key = fe.getFromNode().getIcao() + "→" + fe.getToNode().getIcao();
                    routeCount.merge(key, 1, Integer::sum);
                }
            }
        }

        List<ReportView.TopRoute> topRoutes = routeCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(10)
                .map(e -> {
                    String[] parts = e.getKey().split("→");
                    return new ReportView.TopRoute(parts[0], parts[1], e.getValue());
                })
                .toList();

        int totalBaggages = active.size() + delivered.size();
        int slaBreaches   = countSlaBreaches(runner, graph, simNow);
        double elapsedH   = Duration.between(session.getConfig().simStart(), simNow).toSeconds() / 3600.0;
        double throughput = elapsedH > 0 ? Math.round(delivered.size() / elapsedH * 10.0) / 10.0 : 0.0;

        return new ReportView(
                session.getConfig().simStart(),
                session.getConfig().simEnd(),
                session.getStatus().name().toLowerCase(),
                shipmentIds.size(),
                totalBaggages,
                delivered.size(),
                slaBreaches,
                pending,
                inFlight,
                throughput,
                topRoutes);
    }

    @Override
    public List<com.tasf.b2b.domain.simulator.dto.SlaBreachInfo> getSlaBreaches(String sessionId) {
        SimulationSession session = registry.findOrThrow(sessionId);
        return session.getRunner().getSlaBreaches();
    }

    private void requireAirport(SpaceTimeGraph graph, String icao) {
        if (graph.getAirport(icao) == null)
            throw new IllegalArgumentException("Aeropuerto no encontrado: " + icao);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static FlightView toFlightView(FlightEdge fe, Instant simNow) {
        return new FlightView(
                fe.getIdFlightEdge(),
                fe.getFromNode().getIcao(),
                fe.getToNode().getIcao(),
                fe.getFromNode().getTimeUtc(),
                fe.getToNode().getTimeUtc(),
                flightStatus(fe, simNow),
                fe.getLoad(),
                fe.getCapacity(),
                FlightView.calcPct(fe.getLoad(), fe.getCapacity()),
                FlightView.calcLevel(fe.getLoad(), fe.getCapacity()));
    }

    private static String flightStatus(FlightEdge fe, Instant simNow) {
        if (fe.isCancelled()) return "CANCELLED";
        Instant dep = fe.getFromNode().getTimeUtc();
        Instant arr = fe.getToNode().getTimeUtc();
        if (dep.isAfter(simNow)) return "SCHEDULED";
        if (arr.isAfter(simNow)) return "DEPARTED";
        return "ARRIVED";
    }

    private int countSlaBreaches(SimulationRunner runner, SpaceTimeGraph graph, Instant simNow) {
        int breaches = 0;
        int pickupMinutes = runner.getConfig().pickupMinutes();

        // Maletas aún no entregadas: hay breach si su deadline ya pasó (no se podrá
        // entregar antes de una fecha que ya quedó atrás).
        List<Baggage> activeSla = new ArrayList<>();
        activeSla.addAll(new ArrayList<>(graph.getPendingBaggages()));
        activeSla.addAll(new ArrayList<>(graph.getAssignedBaggages()));
        for (Baggage b : activeSla) {
            if (b.getDeadlineUtc() != null && b.getDeadlineUtc().isBefore(simNow)) breaches++;
        }
        // Maletas entregadas: hay breach SOLO si la entrega REAL (llegada al destino +
        // minutos de recogida, igual que BaggagePickupEvent) superó el deadline.
        // OJO: comparar el deadline contra simNow estaba mal — contaba como vencida
        // cualquier maleta entregada a tiempo en cuanto el reloj simulado pasaba su
        // deadline, inflando el contador hasta ≈ "entregadas con deadline ya cumplido".
        for (Baggage b : runner.getDeliveredBaggages()) {
            if (b.getDeadlineUtc() == null) continue;
            Instant deliveredAt = actualDeliveryInstant(b, pickupMinutes);
            if (deliveredAt != null && deliveredAt.isAfter(b.getDeadlineUtc())) breaches++;
        }
        return breaches;
    }

    // Hora real en que se entregó la maleta: llegada del último vuelo recorrido al
    // destino + minutos de recogida. Refleja el momento del BaggagePickupEvent.
    private static Instant actualDeliveryInstant(Baggage b, int pickupMinutes) {
        List<STEdge> traveled = b.getRouteTraveled();
        for (int i = traveled.size() - 1; i >= 0; i--) {
            if (traveled.get(i) instanceof FlightEdge fe) {
                return fe.getToNode().getTimeUtc().plus(Duration.ofMinutes(pickupMinutes));
            }
        }
        return null;
    }
}
