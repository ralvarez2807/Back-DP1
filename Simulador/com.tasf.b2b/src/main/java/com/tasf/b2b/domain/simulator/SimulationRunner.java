package com.tasf.b2b.domain.simulator;

import com.tasf.b2b.domain.model.graph.SpaceTimeGraph;
import com.tasf.b2b.domain.model.graph.componentsgraph.FlightEdge;
import com.tasf.b2b.domain.model.graph.componentsgraph.STEdge;
import com.tasf.b2b.domain.model.graph.componentsgraph.WaitEdge;
import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;
import com.tasf.b2b.domain.model.graph.movable.Baggage;
import com.tasf.b2b.domain.model.graph.movable.Shipment;
import com.tasf.b2b.domain.optimizer.alns.BaggageState;
import com.tasf.b2b.domain.simulator.dto.*;
import com.tasf.b2b.domain.simulator.event.*;

import java.time.Duration;

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
    // CopyOnWriteArrayList: el runner solo le hace add() (una entrega a la vez);
    // los hilos de consulta (getReport, buildAllRoutes, dashboard) la leen seguido
    // — mismo motivo que pendingBaggages/assignedBaggages en SpaceTimeGraph, ver
    // ese comentario. Con ArrayList normal, iterarla mientras el runner le hacía
    // add() podía lanzar ConcurrentModificationException.
    private final List<Baggage> deliveredBaggages;
    // IDs entregados para chequeo O(1) en el evento de deadline (SLA).
    private final Set<String>   deliveredIds;

    // Envíos manuales activos: id de envío → nº de maletas aún NO entregadas. Se reserva
    // al inyectar una orden (tryReserveShipmentId) y se decrementa al entregar cada maleta;
    // cuando llega a 0 la entrada se elimina y ese id vuelve a estar libre para reutilizar.
    // ConcurrentHashMap: lo escribe el hilo del runner (entregas) y lo lee/escribe el hilo
    // HTTP al reservar un id en una orden entrante.
    private final Map<String, Integer> activeShipmentRemaining =
            new java.util.concurrent.ConcurrentHashMap<>();

    // Maletas que llegaron a su destino final y esperan la ventana de recojo
    // (pickupMinutes) antes de ser entregadas al pasajero. Durante esa ventana siguen
    // ocupando físicamente el almacén (cuentan en la ocupación) — se retiran al pickup.
    // Keyed by baggageId para remoción O(1) en handleBaggagePickup.
    public record PickupWaiting(Baggage baggage, String icao, Instant pickupAt) {}
    // ConcurrentHashMap: lo escribe el hilo del runner y lo lee el hilo de consultas
    // (getAirportTransit / getAirports) — evita ConcurrentModificationException al copiar.
    private final Map<String, PickupWaiting> awaitingPickup = new java.util.concurrent.ConcurrentHashMap<>();
    // Foto forense de cada incumplimiento de SLA, en orden de ocurrencia.
    // CopyOnWriteArrayList por el mismo motivo que deliveredBaggages: la lee
    // GET /sla-breaches (SlaBreachesModal) mientras el runner le hace add().
    private final List<com.tasf.b2b.domain.simulator.dto.SlaBreachInfo> slaBreaches;

    private volatile boolean running;
    // Razón legible del colapso, si terminó por eso; null si no colapsó.
    private volatile String  collapseReason;

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
        this.deliveredBaggages = new java.util.concurrent.CopyOnWriteArrayList<>();
        this.deliveredIds      = new HashSet<>();
        this.slaBreaches       = new java.util.concurrent.CopyOnWriteArrayList<>();
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
            case HorizonExpandEvent    e -> handleHorizonExpand(e);
            case FlightDepartureEvent  e -> handleFlightDeparture(e);
            case FlightArrivalEvent    e -> handleFlightArrival(e);
            case FlightCancelledEvent  e -> handleFlightCancelled(e);
            case FlightScheduleUpdatedEvent e -> handleFlightScheduleUpdated(e);
            case FlightScheduleRemovedEvent e -> handleFlightScheduleRemoved(e);
            case BaggagePickupEvent    e -> handleBaggagePickup(e);
            case NewShipmentEvent      e -> handleNewShipment(e);
            case SlaDeadlineEvent      e -> handleSlaDeadline(e);
            case RouteSolutionEvent    e -> handleRouteSolution(e);
            case CollapseDetectedEvent e -> handleCollapseDetected(e);
            case SimulationEndEvent    e -> running = false;
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
            baggage.recordHistory(clock.now(), "IN_FLIGHT", fe.getFromNode().getIcao(), fe.getIdFlightEdge());
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
            fe.release(); // libera la carga que assignBaggage reservó para este vuelo

            if (fe.getToNode().getIcao().equals(baggage.getDestIcao())) {
                graph.getAssignedBaggages().remove(baggage);
                Instant pickupTime = fe.getToNode().getTimeUtc()
                        .plus(config.pickupMinutes(), ChronoUnit.MINUTES);
                // Llegó a destino final pero aún NO se entrega: espera la ventana de
                // recojo. Sigue ocupando almacén hasta el BaggagePickupEvent.
                awaitingPickup.put(baggage.getId(),
                        new PickupWaiting(baggage, fe.getToNode().getIcao(), pickupTime));
                baggage.recordHistory(clock.now(), "AWAITING_PICKUP", fe.getToNode().getIcao(), null);
                log("Maleta en recogida: " + baggage.getId() + " → entrega a las " + pickupTime);
                submit(new BaggagePickupEvent(pickupTime, baggage, fe.getToNode().getIcao(), clock));
            } else {
                STEdge waitEdge = graph.getWaitEdgeFrom(fe.getToNode());
                baggage.setCurrentEdge(waitEdge);
                if (waitEdge != null) waitEdge.assign();
                if (baggage.getExpectedRoute().isEmpty()) {
                    graph.unassignBaggage(baggage);
                    baggage.recordHistory(clock.now(), "PENDING", fe.getToNode().getIcao(), null);
                    publisher.publish(new BaggagePendingDTO(
                            clock.now(),
                            baggage.getId(),
                            fe.getToNode().getIcao()));
                } else {
                    baggage.recordHistory(clock.now(), "WAITING", fe.getToNode().getIcao(), null);
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

        // Las maletas que aterrizaron entraron al almacén de destino: si con ellas se
        // supera la capacidad, el almacén se desborda → colapso.
        checkWarehouseOverflow(fe.getToNode().getIcao());
    }


    private void handleBaggagePickup(BaggagePickupEvent e) {
        Baggage baggage = e.getBaggage();
        awaitingPickup.remove(baggage.getId());   // termina la ventana de recojo
        deliveredBaggages.add(baggage);
        deliveredIds.add(baggage.getId());
        // Descuenta esta maleta del envío activo; al entregarse la última, libera el id
        // (queda disponible para que una nueva orden lo reutilice).
        String shId = baggage.getShipment().getShipmentData().getId();
        activeShipmentRemaining.computeIfPresent(shId, (k, remaining) -> remaining <= 1 ? null : remaining - 1);
        baggage.recordHistory(clock.now(), "DELIVERED", e.getDestIcao(), null);
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

    // Modifica el horario/capacidad de un schedule recurrente (LE-12). Cancela las
    // instancias ya expandidas y aún no partidas del schedule viejo (replanifica sus
    // maletas, LE-27) y registra el nuevo schedule para las próximas expansiones.
    private void handleFlightScheduleUpdated(FlightScheduleUpdatedEvent e) {
        String oldId = e.getOldScheduleId();
        var    newSchedule = e.getNewSchedule();
        boolean idChanged  = !oldId.equals(newSchedule.getId());

        List<FlightEdge> stale = new ArrayList<>();
        for (FlightEdge fe : graph.getAllFlightEdges()) {
            if (!fe.isCancelled()
                    && fe.getFlightScheduleData().getId().equals(oldId)
                    && fe.getFromNode().getTimeUtc().isAfter(clock.now())) {
                stale.add(fe);
            }
        }

        if (idChanged) graph.removeScheduledFlight(oldId);
        graph.addScheduledFlight(newSchedule);
        log("Schedule actualizado: " + oldId + " → " + newSchedule.getId()
                + " (" + stale.size() + " instancia(s) futura(s) a replanificar)");

        for (FlightEdge fe : stale) {
            handleFlightCancelled(new FlightCancelledEvent(
                    clock.now(), oldId, fe.getFromNode().getTimeUtc(), clock));
        }
    }

    // Elimina un schedule recurrente de esta sesión (LE — borrado de vuelos). Cancela sus
    // instancias ya expandidas y aún no partidas (replanificando sus maletas) y retira el
    // schedule del grafo para que no se vuelva a expandir. No registra reemplazo.
    private void handleFlightScheduleRemoved(FlightScheduleRemovedEvent e) {
        String scheduleId = e.getScheduleId();

        List<FlightEdge> stale = new ArrayList<>();
        for (FlightEdge fe : graph.getAllFlightEdges()) {
            if (!fe.isCancelled()
                    && fe.getFlightScheduleData().getId().equals(scheduleId)
                    && fe.getFromNode().getTimeUtc().isAfter(clock.now())) {
                stale.add(fe);
            }
        }

        graph.removeScheduledFlight(scheduleId);
        log("Schedule eliminado: " + scheduleId
                + " (" + stale.size() + " instancia(s) futura(s) a replanificar)");

        for (FlightEdge fe : stale) {
            handleFlightCancelled(new FlightCancelledEvent(
                    clock.now(), scheduleId, fe.getFromNode().getTimeUtc(), clock));
        }
    }

    private void handleNewShipment(NewShipmentEvent e) {
        Shipment shipment = new Shipment(e.getShipmentData());
        graph.addShipment(shipment);
        log("Nuevo shipment: " + e.getShipmentData().getId()
                + " (" + graph.getPendingBaggages().size() + " maletas pendientes)");

        // Programa un chequeo de SLA en el instante del deadline de cada maleta.
        for (Baggage b : shipment.getBaggages()) {
            b.recordHistory(clock.now(), "PENDING", e.getShipmentData().getOriginAirport().getIcao(), null);
            if (b.getDeadlineUtc() != null) {
                submit(new SlaDeadlineEvent(b.getDeadlineUtc(), b, clock));
            }
        }

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

        // El nuevo envío deposita sus maletas en el almacén de origen: comprobar
        // que no lo desborde.
        checkWarehouseOverflow(e.getShipmentData().getOriginAirport().getIcao());
    }

    // Dispara en el deadline de una maleta: si no llegó, captura la foto forense.
    private void handleSlaDeadline(SlaDeadlineEvent e) {
        Baggage b = e.getBaggage();
        if (deliveredIds.contains(b.getId())) return;   // llegó a tiempo, no hay breach

        Instant deadline = b.getDeadlineUtc();
        STEdge  cur      = b.getCurrentEdge();

        // Entrega a tiempo aún en curso: el vuelo final ya aterrizó en el destino y
        // la recogida cae justo en el deadline. El BaggagePickupEvent comparte
        // instante con este evento en la DelayQueue y puede procesarse después —
        // no es incumplimiento.
        if (cur instanceof FlightEdge fe
                && fe.getToNode().getIcao().equals(b.getDestIcao())
                && !fe.getToNode().getTimeUtc()
                      .plus(config.pickupMinutes(), ChronoUnit.MINUTES).isAfter(deadline)) {
            return;
        }

        String status, location;
        if (cur instanceof FlightEdge fe) {
            status = "IN_FLIGHT";
            location = fe.getFromNode().getIcao();        // de dónde salió el vuelo en curso
        } else if (b.isUnassigned()) {
            status = "PENDING";
            location = b.getCurrentAirport();
        } else {
            status = "WAITING";
            location = b.getCurrentAirport();
        }

        boolean hadRoute = b.isRouteComplete();
        Instant eta      = null;
        long    lateMin  = 0;
        if (hadRoute) {
            eta = b.getExpectedDestEdge().getToNode().getTimeUtc()
                    .plus(config.pickupMinutes(), ChronoUnit.MINUTES);
            lateMin = Duration.between(deadline, eta).toMinutes();
        }

        String cause;
        if (b.isUnassigned()) {
            cause = "SIN RUTA al vencer: el planificador nunca le asignó una ruta. "
                  + "No es capacidad de almacén/vuelo — quedó sin plan.";
        } else if (!hadRoute) {
            cause = "Ruta incompleta al vencer: solo tenía plan hasta un punto intermedio ("
                  + location + "), sin tramo final al destino.";
        } else if ("IN_FLIGHT".equals(status)) {
            cause = "En tránsito pero su ruta planificada ya llegaba tarde (ETA " + eta
                  + ", " + lateMin + " min pasado el deadline).";
        } else {
            cause = "Tenía ruta asignada pero su ETA (" + eta + ") superaba el deadline en "
                  + lateMin + " min: la ruta elegida era demasiado lenta.";
        }

        // Tramos del plan para inspección (recorridos + en curso + planificados)
        List<com.tasf.b2b.domain.simulator.dto.SlaBreachInfo.Leg> legs = new ArrayList<>();
        for (STEdge edge : b.getRouteTraveled()) {
            if (edge instanceof FlightEdge fe) legs.add(legOf(fe, "ARRIVED"));
        }
        for (STEdge edge : b.getExpectedRoute()) {
            if (edge instanceof FlightEdge fe) legs.add(legOf(fe, edge == cur ? "DEPARTED" : "PLANNED"));
        }

        var info = new com.tasf.b2b.domain.simulator.dto.SlaBreachInfo(
                deadline, b.getId(), b.getShipment().getShipmentData().getId(),
                b.getShipment().getShipmentData().getOriginAirport().getIcao(),
                b.getDestIcao(), deadline, status, location, hadRoute, eta, lateMin, cause, legs);
        slaBreaches.add(info);

        log("*** SLA VENCIDO *** " + b.getId() + " @ " + location + " (" + status + ") | " + cause);

        // SLA vencido ES el colapso del negocio, sin importar si la maleta tenía
        // ruta asignada o iba en vuelo. El CollapseDetector del hilo ALNS solo ve
        // maletas pendientes (sin ruta); este evento cubre los demás estados y
        // fija el momento del colapso en el instante exacto del deadline.
        if (config.collapseOnFailure()) {
            BaggageState state = new BaggageState(
                    b.getId(), location, deadline, b.getDestIcao(), deadline);
            handleCollapseDetected(new CollapseDetectedEvent(
                    deadline,
                    new CollapseDetector.CollapseInfo(
                            CollapseDetector.CollapseReason.DEADLINE_EXCEEDED,
                            List.of(state), 0),
                    clock));
        }
    }

    private com.tasf.b2b.domain.simulator.dto.SlaBreachInfo.Leg legOf(FlightEdge fe, String state) {
        return new com.tasf.b2b.domain.simulator.dto.SlaBreachInfo.Leg(
                fe.getFromNode().getIcao(), fe.getToNode().getIcao(),
                fe.getFromNode().getTimeUtc(), fe.getToNode().getTimeUtc(), state);
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

            // Solución duplicada: la maleta ya tiene plan vigente (otra solución ALNS
            // o el fallback genético la asignó mientras este solve estaba en curso).
            // Aplicarla haría clearExpectedRoute sin liberar los asientos del plan
            // anterior (reservas huérfanas) y la duplicaría en assignedBaggages.
            if (!baggage.isUnassigned()) {
                stale++;
                continue;
            }

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

    // Colapso por desborde de almacén: si el número de maletas físicamente esperando
    // en un aeropuerto supera su capacidad (101 %+), el almacén se desborda y eso es un
    // colapso operativo — igual que un SLA vencido. Solo aplica cuando el escenario
    // detecta colapsos (collapseOnFailure); "día a día" no colapsa.
    private void checkWarehouseOverflow(String icao) {
        if (!config.collapseOnFailure()) return;
        if (icao == null) return;

        AirportDataDTO airport = graph.getAirport(icao);
        if (airport == null) return;
        int capacity = airport.getCapacity();
        if (capacity <= 0) return;

        // Carga = maletas cuyo edge actual es un WaitEdge en este aeropuerto
        // (WAITING + PENDING físicamente en almacén; IN_FLIGHT y DELIVERED no ocupan)
        // MÁS las que esperan recojo en su destino final (ocupan hasta el pickup).
        int load = 0;
        for (Baggage b : graph.getPendingBaggages()) {
            STEdge cur = b.getCurrentEdge();
            if (cur instanceof WaitEdge we && icao.equals(we.getFromNode().getIcao())) load++;
        }
        for (Baggage b : graph.getAssignedBaggages()) {
            STEdge cur = b.getCurrentEdge();
            if (cur instanceof WaitEdge we && icao.equals(we.getFromNode().getIcao())) load++;
        }
        for (PickupWaiting pw : awaitingPickup.values()) {
            if (icao.equals(pw.icao())) load++;
        }

        if (load > capacity) {
            log(String.format("*** ALMACÉN DESBORDADO *** %s: %d/%d maletas (>100%%)", icao, load, capacity));
            // primaryBaggageId lleva el ICAO del almacén desbordado (no una maleta).
            BaggageState state = new BaggageState(icao, icao, clock.now(), icao, clock.now());
            handleCollapseDetected(new CollapseDetectedEvent(
                    clock.now(),
                    new CollapseDetector.CollapseInfo(
                            CollapseDetector.CollapseReason.WAREHOUSE_OVERFLOW,
                            List.of(state), 0),
                    clock));
        }
    }

    private void handleCollapseDetected(CollapseDetectedEvent e) {
        if (!running) return;   // ya colapsó/terminó — no re-disparar
        CollapseDetector.CollapseInfo info = e.getInfo();
        String reasonLabel = switch (info.reason()) {
            case DEADLINE_EXCEEDED  -> String.format(
                    "deadline superado por %d min",
                    Duration.between(info.primaryDeadline(), clock.now()).toMinutes());
            case NO_VIABLE_ROUTE    -> String.format(
                    "sin ruta viable tras %d ciclos consecutivos", info.consecutiveCycles());
            case WAREHOUSE_OVERFLOW -> String.format(
                    "almacén %s superó su capacidad de almacenamiento", info.primaryBaggageId());
        };
        log(String.format(
                "*** COLAPSO *** maleta=%s | razón=%s | deadline=%s",
                info.primaryBaggageId(), reasonLabel, info.primaryDeadline()));

        this.collapseReason = reasonLabel;

        publisher.publish(new CollapseDetectedDTO(
                clock.now(), info.reason(), info.primaryBaggageId(),
                info.primaryDeadline(), info.consecutiveCycles()));

        running = false;
    }

    /** Razón legible del colapso, si terminó por eso; null si no colapsó. */
    public String getCollapseReason() { return collapseReason; }

    private void log(String msg) {
        System.out.printf("[SIM %s] %s%n", clock.now(), msg);
    }
    private void runOptimizationCycle() {
        log("Ejecutando ciclo de optimización...");
        graph.optimizeAndAssignRoutes(config.pickupMinutes(), clock.now());

        int pendingCount = graph.getPendingBaggages().size();
        int assignedCount = graph.getAssignedBaggages().size();

        log("Optimización completada - Pendientes: " + pendingCount +
                ", Asignadas: " + assignedCount);
    }

    // ── Reserva de ids de envío (órdenes manuales) ─────────────────────────────

    /**
     * Reserva un id de envío para una orden manual entrante. Devuelve {@code true} si el
     * id quedó reservado (no había un envío activo con ese id); {@code false} si ya hay
     * uno activo (sus maletas aún no se entregan todas) — el llamador debe rechazar la
     * orden. El id se libera solo cuando se entrega la última maleta del envío
     * (ver {@link #handleBaggagePickup}). Thread-safe (ConcurrentHashMap.putIfAbsent).
     */
    public boolean tryReserveShipmentId(String shipmentId, int quantity) {
        return activeShipmentRemaining.putIfAbsent(shipmentId, Math.max(1, quantity)) == null;
    }

    // ── Consultas de estado ───────────────────────────────────────────────────

    public List<Baggage> getDeliveredBaggages() {
        return Collections.unmodifiableList(deliveredBaggages);
    }

    /** Maletas en su destino final esperando la ventana de recojo del pasajero. */
    public List<PickupWaiting> getAwaitingPickup() {
        return new ArrayList<>(awaitingPickup.values());
    }

    /** Foto forense de cada incumplimiento de SLA, en orden de ocurrencia. */
    public List<com.tasf.b2b.domain.simulator.dto.SlaBreachInfo> getSlaBreaches() {
        return Collections.unmodifiableList(slaBreaches);
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
