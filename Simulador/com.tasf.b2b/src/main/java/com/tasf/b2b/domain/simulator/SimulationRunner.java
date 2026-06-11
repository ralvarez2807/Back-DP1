package com.tasf.b2b.domain.simulator;

import com.tasf.b2b.domain.model.graph.SpaceTimeGraph;
import com.tasf.b2b.domain.model.graph.componentsgraph.FlightEdge;
import com.tasf.b2b.domain.model.graph.componentsgraph.STEdge;
import com.tasf.b2b.domain.model.graph.movable.Baggage;
import com.tasf.b2b.domain.model.graph.movable.Shipment;
import com.tasf.b2b.domain.simulator.dto.*;
import com.tasf.b2b.domain.simulator.event.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.DelayQueue;

/**
 * Loop principal de la simulación en tiempo real escalado.
 *
 * Un solo hilo consume eventos de la DelayQueue; el tiempo de espera real entre
 * eventos es (simOffset / speedFactor), lo que implementa el factor de velocidad.
 *
 * Hilos externos (ALNS, inyector de maletas, cancelaciones) pueden llamar submit()
 * de forma thread-safe en cualquier momento. El evento se integra en la cola y el
 * loop lo procesa cuando llegue su hora de simulación.
 *
 * SpaceTimeGraph solo se muta desde este hilo, así que no necesita sincronización
 * interna. El hilo ALNS debe trabajar sobre una copia/snapshot de los datos que
 * necesita y devolver resultados vía RouteSolutionEvent.
 */
public class SimulationRunner implements Runnable {

    private final DelayQueue<SimEvent> eventQueue;
    private final SimulationClock      clock;
    private final SpaceTimeGraph       graph;
    private final SimulationConfig     config;
    private final Instant              simEnd;
    private final StatePublisher       publisher;

    //TODO: deliveredBaggages luego no servirá — se guardará un histórico en PostgreSQL
    private final List<Baggage> deliveredBaggages;

    private volatile boolean running;

    // ── Stats de soluciones ALNS ──────────────────────────────────────────────
    private int solutionCount  = 0;
    private int totalProposed  = 0;
    private int totalStale     = 0;
    private int totalApplied   = 0;

    public SimulationRunner(SpaceTimeGraph graph, SimulationConfig config, StatePublisher publisher) {
        this.graph             = graph;
        this.config            = config;
        this.publisher         = publisher;
        this.clock             = new SimulationClock(config.simStart(), config.speedFactor());
        this.simEnd            = config.simEnd();
        this.eventQueue        = new DelayQueue<>();
        this.deliveredBaggages = new ArrayList<>();
        this.running           = false;
    }

    /**
     * Siembra los eventos de arranque.
     * Debe llamarse antes de new Thread(runner).start().
     */
    public void init() {
        running = true;
        Instant startDay = clock.getSimStart().truncatedTo(ChronoUnit.DAYS);
        submit(new HorizonExpandEvent(startDay, clock));
        submit(new SimulationEndEvent(simEnd, clock));
    }

    /** Inyecta un evento desde cualquier hilo. Thread-safe. */
    public void submit(SimEvent event) {
        eventQueue.put(event);
    }

    /** Pausa el reloj: los eventos dejan de dispararse hasta resume(). Thread-safe. */
    public void pause()  { clock.pause(); }

    /** Reanuda el reloj desde donde se pausó. Thread-safe. */
    public void resume() { clock.resume(); }

    @Override
    public void run() {
        while (running) {
            try {
                SimEvent event = eventQueue.take();
                process(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    // ── Despacho ─────────────────────────────────────────────────────────────

    private void process(SimEvent event) {
        switch (event) {
            case HorizonExpandEvent   e -> handleHorizonExpand(e);
            case FlightDepartureEvent e -> handleFlightDeparture(e);
            case FlightArrivalEvent   e -> handleFlightArrival(e);
            case FlightCancelledEvent e -> handleFlightCancelled(e);
            case BaggagePickupEvent   e -> handleBaggagePickup(e);
            case NewShipmentEvent     e -> handleNewShipment(e);
            case RouteSolutionEvent   e -> handleRouteSolution(e);
            case SimulationEndEvent   e -> running = false;
            default -> throw new IllegalStateException(
                    "Evento sin handler: " + event.getClass().getSimpleName());
        }
    }


    // ── Handlers ─────────────────────────────────────────────────────────────

    private void handleHorizonExpand(HorizonExpandEvent e) {
        List<FlightEdge> newFlights = graph.expandAllFlights(e.getSimTime());

        for (FlightEdge fe : newFlights) {
            Instant dep = fe.getFromNode().getTimeUtc();
            Instant arr = fe.getToNode().getTimeUtc();

            if (!dep.isBefore(clock.getSimStart())) {
                submit(new FlightDepartureEvent(dep, fe, clock));
                submit(new FlightArrivalEvent(arr, fe, clock));
            }

            publisher.publish(new FlightScheduledDTO(
                    clock.now(),
                    fe.getIdFlightEdge(),
                    fe.getFromNode().getIcao(),
                    fe.getToNode().getIcao(),
                    dep,
                    fe.getCapacity()));
        }

        Instant nextDay = e.getSimTime().plus(1, ChronoUnit.DAYS);
        if (nextDay.isBefore(simEnd)) {
            submit(new HorizonExpandEvent(nextDay, clock));
        }
    }

    private void handleFlightDeparture(FlightDepartureEvent e) {
        FlightEdge fe = e.getFlightEdge();
        if (fe.isCancelled()) return;

        log("Vuelo parte  : " + fe.getIdFlightEdge());

        for (Baggage baggage : new ArrayList<>(graph.getAssignedBaggages())) {
            if (baggage.peekNextEdge() != fe) continue;
            STEdge prev = baggage.getCurrentEdge();
            if (prev != null) prev.release();
            baggage.setCurrentEdge(fe);
            log("Maleta embarca: " + baggage.getId());
            publisher.publish(new BaggageDepartedDTO(
                    clock.now(),
                    baggage.getId(),
                    fe.getIdFlightEdge(),
                    fe.getFromNode().getIcao(),
                    fe.getToNode().getIcao()));
        }

        publisher.publish(new FlightDepartedDTO(
                clock.now(),
                fe.getIdFlightEdge(),
                fe.getFromNode().getIcao(),
                fe.getToNode().getIcao(),
                fe.getLoad(),
                fe.getCapacity()));
    }

    private void handleFlightArrival(FlightArrivalEvent e) {
        FlightEdge fe = e.getFlightEdge();
        if (fe.isCancelled()) return;

        log("Vuelo llega  : " + fe.getIdFlightEdge());

        int arrivedCount = 0;
        for (Baggage baggage : new ArrayList<>(graph.getAssignedBaggages())) {
            if (baggage.getCurrentEdge() != fe) continue;
            arrivedCount++;

            baggage.confirmNextEdge();

            if (fe.getToNode().getIcao().equals(baggage.getDestIcao())) {
                graph.getAssignedBaggages().remove(baggage);
                Instant pickupTime = fe.getToNode().getTimeUtc()
                        .plus(config.pickupMinutes(), ChronoUnit.MINUTES);
                log("Maleta en recogida: " + baggage.getId() + " → entrega a las " + pickupTime);
                submit(new BaggagePickupEvent(pickupTime, baggage, fe.getToNode().getIcao(), clock));
            } else {
                STEdge waitEdge = graph.getWaitEdgeFrom(fe.getToNode());
                baggage.setCurrentEdge(waitEdge);
                if (waitEdge != null) waitEdge.assign();
                if (baggage.getExpectedRoute().isEmpty()) {
                    graph.unassignBaggage(baggage);
                    publisher.publish(new BaggagePendingDTO(
                            clock.now(),
                            baggage.getId(),
                            fe.getToNode().getIcao()));
                } else {
                    publisher.publish(new BaggageArrivedDTO(
                            clock.now(),
                            baggage.getId(),
                            fe.getIdFlightEdge(),
                            fe.getToNode().getIcao()));
                }
            }
        }

        publisher.publish(new FlightArrivedDTO(
                clock.now(),
                fe.getIdFlightEdge(),
                fe.getToNode().getIcao(),
                arrivedCount));
    }


    private void handleBaggagePickup(BaggagePickupEvent e) {
        Baggage baggage = e.getBaggage();
        deliveredBaggages.add(baggage);
        log("Maleta entregada: " + baggage.getId());
        publisher.publish(new BaggageDeliveredDTO(
                clock.now(),
                baggage.getId(),
                e.getDestIcao()));
    }

    private void handleFlightCancelled(FlightCancelledEvent e) {
        boolean accepted = graph.cancelFlight(e.getFlightScheduleKey(), e.getDepTimeUtc());
        log("Cancelación  : " + e.getFlightScheduleKey() + " → " + (accepted ? "OK" : "ignorada"));
        if (!accepted) return;

        publisher.publish(new FlightCancelledDTO(
                clock.now(),
                e.getFlightScheduleKey(),
                e.getDepTimeUtc()));

        for (Baggage baggage : graph.getBaggagesAffectedBy(e.getFlightScheduleKey(), e.getDepTimeUtc())) {
            graph.unassignBaggage(baggage);
            publisher.publish(new BaggagePendingDTO(
                    clock.now(),
                    baggage.getId(),
                    baggage.getCurrentAirport()));
        }

        runOptimizationCycle();  // ← Agregar aquí
    }

    private void handleNewShipment(NewShipmentEvent e) {
        Shipment shipment = new Shipment(e.getShipmentData());
        graph.addShipment(shipment);
        log("Nuevo shipment: " + e.getShipmentData().getId()
                + " (" + graph.getPendingBaggages().size() + " maletas pendientes)");

        List<String> baggageIds = shipment.getBaggages().stream()
                .map(Baggage::getId)
                .toList();
        publisher.publish(new ShipmentCreatedDTO(
                clock.now(),
                e.getShipmentData().getId(),
                baggageIds,
                e.getShipmentData().getOriginAirport().getIcao(),
                e.getShipmentData().getDestAirport().getIcao(),
                shipment.getDeadlineUtc()));
    }

    private void handleRouteSolution(RouteSolutionEvent e) {
        solutionCount++;
        Instant now     = clock.now();
        int stale       = 0;
        int applied     = 0;
        int proposed    = e.getRoutes().size();

        for (Map.Entry<Baggage, List<STEdge>> entry : e.getRoutes().entrySet()) {
            Baggage      baggage = entry.getKey();
            List<STEdge> route   = entry.getValue();

            // Solución obsoleta: el primer vuelo ya partió antes de que llegara la solución.
            // Dejar el baggage en pending para que la siguiente iteración ALNS lo re-enrute.
            if (!route.isEmpty() && route.get(0) instanceof FlightEdge first
                    && !first.getFromNode().getTimeUtc().isAfter(now)) {
                stale++;
                continue;
            }

            baggage.clearExpectedRoute();
            for (STEdge edge : route) {
                baggage.appendExpectedEdge(edge);
            }
            graph.assignBaggage(baggage);
            applied++;

            List<String> flightIds = route.stream()
                    .filter(edge -> edge instanceof FlightEdge)
                    .map(edge -> ((FlightEdge) edge).getIdFlightEdge())
                    .toList();
            publisher.publish(new BaggageAssignedDTO(
                    clock.now(),
                    baggage.getId(),
                    flightIds));
        }

        totalProposed += proposed;
        totalStale    += stale;
        totalApplied  += applied;

        log(String.format(
                "ALNS #%d | propuestas: %d | obsoletas: %d (%.0f%%) | aplicadas: %d | sin-ruta(ALNS): %d | score: %.1f",
                solutionCount, proposed,
                stale, proposed > 0 ? 100.0 * stale / proposed : 0.0,
                applied, e.getUnroutedCount(), e.getAlnsScore()));
    }

    private void log(String msg) {
        System.out.printf("[SIM %s] %s%n", clock.now(), msg);
    }
    private void runOptimizationCycle() {
        log("Ejecutando ciclo de optimización...");
        graph.optimizeAndAssignRoutes();

        int pendingCount = graph.getPendingBaggages().size();
        int assignedCount = graph.getAssignedBaggages().size();

        log("Optimización completada - Pendientes: " + pendingCount +
                ", Asignadas: " + assignedCount);
    }

    // ── Consultas de estado ───────────────────────────────────────────────────

    public List<Baggage> getDeliveredBaggages() {
        return Collections.unmodifiableList(deliveredBaggages);
    }

    public SimulationClock   getClock()  { return clock; }
    public SpaceTimeGraph    getGraph()  { return graph; }
    public SimulationConfig  getConfig() { return config; }
    public boolean           isRunning() { return running; }

    public int getSolutionCount() { return solutionCount; }
    public int getTotalProposed() { return totalProposed; }
    public int getTotalStale()    { return totalStale; }
    public int getTotalApplied()  { return totalApplied; }

}
