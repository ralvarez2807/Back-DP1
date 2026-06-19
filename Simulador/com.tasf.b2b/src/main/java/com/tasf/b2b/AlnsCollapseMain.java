package com.tasf.b2b;

import com.tasf.b2b.domain.model.graph.SpaceTimeGraph;
import com.tasf.b2b.domain.model.graph.componentsgraph.FlightEdge;
import com.tasf.b2b.domain.model.graph.componentsgraph.STEdge;
import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;
import com.tasf.b2b.domain.model.graph.immovable.DeliveryTypeValues;
import com.tasf.b2b.domain.model.graph.immovable.FlightScheduleDataDTO;
import com.tasf.b2b.domain.model.graph.immovable.ShipmentDataDTO;
import com.tasf.b2b.domain.model.graph.movable.Baggage;
import com.tasf.b2b.domain.model.graph.movable.Shipment;
import com.tasf.b2b.domain.optimizer.SolutionResult;
import com.tasf.b2b.domain.optimizer.alns.ALNSAlgorithm;
import com.tasf.b2b.domain.optimizer.alns.AlnsProjection;
import com.tasf.b2b.domain.optimizer.alns.AlnsProjectionBuilder;
import com.tasf.b2b.infrastructure.files.AirportParser;
import com.tasf.b2b.infrastructure.files.FlightParser;
import com.tasf.b2b.infrastructure.files.TxtShipmentFeed;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Busca el día de colapso del ALNS usando un simulador event-driven puro.
 *
 * No usa reloj de pared ni speed factor: salta directamente de un evento
 * al siguiente en tiempo simulado. El ALNS se ejecuta sincrónicamente
 * cada vez que hay maletas pendientes. No modifica SimulationMain.
 *
 * Cambia SCAN_FROM y MAX_DAYS para ajustar el rango.
 */
public class AlnsCollapseMain {

    private static final String SCAN_FROM = "2026-01-01";
    private static final int    MAX_DAYS  = 30;

    private static final int MIN_CONN_MINUTES = 10;
    private static final int PICKUP_MINUTES   = 10;

    // ── Tipos de evento internos (sin reloj de pared) ─────────────────────────
    private sealed interface Ev permits Ev.Ship, Ev.Dep, Ev.Arr {
        Instant t();
        record Ship(Instant t, ShipmentDataDTO data) implements Ev {}
        record Dep (Instant t, FlightEdge flight)    implements Ev {}
        record Arr (Instant t, FlightEdge flight)    implements Ev {}
    }

    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {

        // ── 1. Datos estáticos ────────────────────────────────────────────────
        Map<String, AirportDataDTO> airports = AirportParser.parse(
                resourcePath("c.1inf54.26.1.v1.Aeropuerto.husos.v1.20250818__estudiantes.txt"));
        List<FlightScheduleDataDTO> flights = FlightParser.parse(
                resourcePath("planes_vuelo.txt"), airports);
        DeliveryTypeValues deliveryTypes = new DeliveryTypeValues();
        Path shipmentDir = resourcePath("_envios_preliminar_").getParent()
                .resolve("_envios_preliminar_");

        System.out.printf("[COLLAPSE] Aeropuertos: %d | Programaciones vuelo: %d%n",
                airports.size(), flights.size());

        // ── 2. Grafo único (persiste entre días: maletas y capacidades acumulan) ─
        SpaceTimeGraph graph = new SpaceTimeGraph();
        airports.values().forEach(graph::addAirport);
        flights.forEach(graph::addScheduledFlight);

        // ── 3. Expandir todos los vuelos del rango de una vez ─────────────────
        LocalDate scanStart     = LocalDate.parse(SCAN_FROM);
        Instant   scanStartInst = scanStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant   scanEndInst   = scanStart.plusDays(MAX_DAYS + 1)
                                           .atStartOfDay(ZoneOffset.UTC).toInstant();

        PriorityQueue<Ev> queue = new PriorityQueue<>(Comparator.comparing(Ev::t));

        for (int d = 0; d <= MAX_DAYS; d++) {
            Instant day = scanStart.plusDays(d).atStartOfDay(ZoneOffset.UTC).toInstant();
            for (FlightEdge fe : graph.expandAllFlights(day)) {
                if (!fe.isCancelled()) {
                    queue.add(new Ev.Dep(fe.getFromNode().getTimeUtc(), fe));
                    queue.add(new Ev.Arr(fe.getToNode().getTimeUtc(),   fe));
                }
            }
        }

        // ── 4. Cargar todos los envíos del rango ─────────────────────────────
        TxtShipmentFeed feed = new TxtShipmentFeed(shipmentDir, airports, deliveryTypes);
        int totalShipEvents = 0;
        ShipmentDataDTO dto;
        while ((dto = feed.next()) != null) {
            Instant t = dto.getEntryDateTimeUtc();
            if (t.isBefore(scanStartInst)) continue;
            if (!t.isBefore(scanEndInst))  break;
            queue.add(new Ev.Ship(t, dto));
            totalShipEvents++;
        }
        System.out.printf("[COLLAPSE] Eventos en cola: %d envíos + vuelos%n",
                totalShipEvents);

        // ── 5. Bucle event-driven ─────────────────────────────────────────────
        ALNSAlgorithm alns     = new ALNSAlgorithm();
        Set<String>   delivered = new HashSet<>();

        int     dayIndex    = 0;
        Instant dayEnd      = scanStartInst.plus(1, ChronoUnit.DAYS);
        int     totalAdded  = 0;
        int     alnsRunsDay = 0;
        long    alnsMsDay   = 0;
        boolean collapsed   = false;

        printHeader();

        while (!queue.isEmpty() && dayIndex < MAX_DAYS) {

            Instant t = queue.peek().t();

            // ── Fin de día: reportar y avanzar ───────────────────────────────
            if (!t.isBefore(dayEnd)) {
                String date    = scanStart.plusDays(dayIndex).toString();
                int    pending = graph.getPendingBaggages().size();
                int    transit = graph.getAssignedBaggages().size();
                int    delivd  = delivered.size();

                String flag = "";
                if (!collapsed && pending > 0) {
                    flag = "  ◄ PRIMER COLAPSO";
                    collapsed = true;
                } else if (collapsed) {
                    flag = "  ✗";
                }

                System.out.printf(
                        "║  %-12s ║ %5d ║ %8d ║ %8d ║ %8d ║ %4d / %5d ms ║%s%n",
                        date, totalAdded, delivd, transit, pending,
                        alnsRunsDay, alnsMsDay, flag);

                if (collapsed) break;

                dayIndex++;
                dayEnd      = dayEnd.plus(1, ChronoUnit.DAYS);
                alnsRunsDay = 0;
                alnsMsDay   = 0;
                continue;
            }

            // ── Procesar TODOS los eventos en tiempo t ────────────────────────
            boolean newPending = false;
            while (!queue.isEmpty() && queue.peek().t().equals(t)) {
                Ev ev = queue.poll();
                switch (ev) {
                    case Ev.Ship s -> {
                        Shipment ship = new Shipment(s.data());
                        graph.addShipment(ship);
                        totalAdded += ship.getBaggages().size();
                        newPending  = true;
                    }
                    case Ev.Dep d -> onDeparture(graph, d.flight());
                    case Ev.Arr a -> onArrival(graph, a.flight(), delivered);
                }
            }

            // ── Ejecutar ALNS si hay pendientes (sincrónicamente) ─────────────
            if (!graph.getPendingBaggages().isEmpty()) {
                AlnsProjection proj = AlnsProjectionBuilder.build(
                        graph, t, MIN_CONN_MINUTES, PICKUP_MINUTES);
                long t0 = System.nanoTime();
                SolutionResult result = alns.optimize(proj);
                alnsMsDay += (System.nanoTime() - t0) / 1_000_000;
                alnsRunsDay++;
                applyRoutes(graph, result);
            }
        }

        // Reporte del último día si no colapsó antes del límite
        if (!collapsed && dayIndex < MAX_DAYS) {
            String date    = scanStart.plusDays(dayIndex).toString();
            int    pending = graph.getPendingBaggages().size();
            int    transit = graph.getAssignedBaggages().size();
            System.out.printf(
                    "║  %-12s ║ %5d ║ %8d ║ %8d ║ %8d ║ %4d / %5d ms ║%n",
                    date, totalAdded, delivered.size(), transit, pending,
                    alnsRunsDay, alnsMsDay);
        }

        printFooter();

        if (!collapsed) {
            System.out.println("[COLLAPSE] Sin colapso en " + MAX_DAYS + " días — aumenta MAX_DAYS.");
        }
    }

    // ══ Handlers de eventos ══════════════════════════════════════════════════

    private static void onDeparture(SpaceTimeGraph graph, FlightEdge flight) {
        for (Baggage b : new ArrayList<>(graph.getAssignedBaggages())) {
            if (b.peekNextEdge() != flight) continue;
            STEdge prev = b.getCurrentEdge();
            if (prev != null) prev.release();   // libera WaitEdge anterior
            b.setCurrentEdge(flight);            // embarcar (load ya fue asignado en assignBaggage)
        }
    }

    private static void onArrival(SpaceTimeGraph graph, FlightEdge flight,
                                  Set<String> delivered) {
        for (Baggage b : new ArrayList<>(graph.getAssignedBaggages())) {
            if (b.getCurrentEdge() != flight) continue;

            b.confirmNextEdge();   // mueve el vuelo a routeTraveled

            if (flight.getToNode().getIcao().equals(b.getDestIcao())) {
                // Entregada en destino
                graph.getAssignedBaggages().remove(b);
                delivered.add(b.getId());
            } else {
                // Escala intermedia: esperar en el aeropuerto
                STEdge waitEdge = graph.getWaitEdgeFrom(flight.getToNode());
                b.setCurrentEdge(waitEdge);
                if (waitEdge != null) waitEdge.assign();
                if (b.getExpectedRoute().isEmpty()) {
                    // Ruta agotada sin llegar al destino → volver a pendiente
                    graph.unassignBaggage(b);
                }
            }
        }
    }

    private static void applyRoutes(SpaceTimeGraph graph, SolutionResult result) {
        for (Map.Entry<Baggage, List<STEdge>> entry : result.routes().entrySet()) {
            Baggage      baggage = entry.getKey();
            List<STEdge> route   = entry.getValue();
            if (route.isEmpty()) continue;

            // Si ya tenía ruta (re-enrutamiento), liberar cargas anteriores
            if (!baggage.isUnassigned()) {
                graph.unassignBaggage(baggage);  // libera FlightEdge.load de ruta vieja
            }
            for (STEdge edge : route) {
                baggage.appendExpectedEdge(edge);
            }
            graph.assignBaggage(baggage);  // registra FlightEdge.load de ruta nueva
        }
    }

    // ══ Presentación ═════════════════════════════════════════════════════════

    private static void printHeader() {
        System.out.println();
        System.out.println("╔══════════════╦═══════╦══════════╦══════════╦══════════╦══════════════════╗");
        System.out.println("║  Día         ║ Total ║ Entreg.  ║ Tránsito ║Pendiente ║ ALNS runs / ms   ║");
        System.out.println("╠══════════════╬═══════╬══════════╬══════════╬══════════╬══════════════════╣");
    }

    private static void printFooter() {
        System.out.println("╚══════════════╩═══════╩══════════╩══════════╩══════════╩══════════════════╝");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static Path resourcePath(String name) throws Exception {
        URI uri = AlnsCollapseMain.class.getClassLoader().getResource(name).toURI();
        return Paths.get(uri);
    }
}
