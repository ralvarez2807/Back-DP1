package com.tasf.b2b;

import com.tasf.b2b.domain.model.graph.SpaceTimeGraph;
import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;
import com.tasf.b2b.domain.model.graph.immovable.DeliveryTypeValues;
import com.tasf.b2b.domain.model.graph.immovable.FlightScheduleDataDTO;
import com.tasf.b2b.domain.model.graph.movable.Baggage;
import com.tasf.b2b.domain.optimizer.RoutingOptimizer;
import com.tasf.b2b.domain.optimizer.alns.ALNSAlgorithm;
import com.tasf.b2b.domain.simulator.SimulationClock;
import com.tasf.b2b.domain.simulator.SimulationConfig;
import com.tasf.b2b.domain.simulator.SimulationRunner;
import com.tasf.b2b.domain.simulator.StatePublisher;
import com.tasf.b2b.domain.simulator.dto.BaggageDeliveredDTO;
import com.tasf.b2b.domain.simulator.thread.AlnsThread;
import com.tasf.b2b.domain.simulator.thread.CancellationInjectorThread;
import com.tasf.b2b.domain.simulator.thread.ShipmentInjectorThread;
import com.tasf.b2b.infrastructure.files.AirportParser;
import com.tasf.b2b.infrastructure.files.FlightParser;
import com.tasf.b2b.infrastructure.files.TxtCancellationFeed;
import com.tasf.b2b.infrastructure.files.TxtShipmentFeed;

import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Prueba el algoritmo ALNS procesando todos los eventos de un día
 * a alta velocidad (sin espera de reloj real).
 *
 * No modifica SimulationMain ni ninguna clase de simulación.
 * Usa SimulationRunner directamente con un SPEED_FACTOR elevado.
 *
 * Dos modos:
 *   SCAN_MODE = false → detalle de un solo día (TEST_DATE)
 *   SCAN_MODE = true  → tabla resumen por días para encontrar el colapso
 */
public class AlnsBenchmarkMain {

    // ── Configura aquí ────────────────────────────────────────────────────────
    private static final boolean SCAN_MODE = false;

    private static final String TEST_DATE = "2026-01-02"; // día a analizar (SCAN_MODE=false)

    private static final String SCAN_FROM = "2026-01-01"; // primer día del escaneo
    private static final int    SCAN_DAYS = 14;           // cuántos días escanear

    // 1 día simulado = 120 s reales → lag ALNS ≈ 4 min sim < 10 min pickup-buffer
    private static final double BENCH_SPEED = 720.0;
    // 1 día simulado = 60 s reales  → lag ALNS ≈ 8 min sim, 14 días ≈ 14 min
    private static final double SCAN_SPEED  = 1_440.0;

    // ─────────────────────────────────────────────────────────────────────────

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm'Z'").withZone(ZoneOffset.UTC);

    public static void main(String[] args) throws Exception {
        Map<String, AirportDataDTO> airports = AirportParser.parse(
                resourcePath("c.1inf54.26.1.v1.Aeropuerto.husos.v1.20250818__estudiantes.txt"));
        List<FlightScheduleDataDTO> flights = FlightParser.parse(
                resourcePath("planes_vuelo.txt"), airports);
        DeliveryTypeValues deliveryTypes = new DeliveryTypeValues();
        Path shipmentDir = resourcePath("_envios_preliminar_").getParent()
                .resolve("_envios_preliminar_");

        System.out.printf("[BENCH] Aeropuertos: %d | Programaciones vuelo: %d%n",
                airports.size(), flights.size());

        if (SCAN_MODE) {
            runScan(airports, flights, deliveryTypes, shipmentDir);
        } else {
            runDay(TEST_DATE, airports, flights, deliveryTypes, shipmentDir, BENCH_SPEED, true);
        }
    }

    // ══ ESCÁNER MULTI-DÍA ════════════════════════════════════════════════════

    private static void runScan(Map<String, AirportDataDTO> airports,
                                List<FlightScheduleDataDTO> flights,
                                DeliveryTypeValues deliveryTypes,
                                Path shipmentDir) throws Exception {
        System.out.println();
        System.out.println("╔══════════════╦═══════╦════════╦════════╦════════╦══════════╦══════════╦════════════╗");
        System.out.println("║  Día         ║ Total ║ Plazo  ║Retraso ║No entg.║ALNSruns  ║  Score   ║ t.ALNS     ║");
        System.out.println("╠══════════════╬═══════╬════════╬════════╬════════╬══════════╬══════════╬════════════╣");

        LocalDate startDate = LocalDate.parse(SCAN_FROM);
        boolean collapsed = false;

        for (int i = 0; i < SCAN_DAYS; i++) {
            String dateStr = startDate.plusDays(i).toString();

            // Suprimir output del simulador durante el escaneo
            PrintStream savedOut = System.out;
            System.setOut(new PrintStream(OutputStream.nullOutputStream()));
            DayResult dr;
            try {
                dr = runDay(dateStr, airports, flights, deliveryTypes, shipmentDir, SCAN_SPEED, false);
            } finally {
                System.setOut(savedOut);
            }

            if (dr == null) {
                System.out.printf("║  %-12s ║  (sin datos para este día)%n", dateStr);
                continue;
            }

            String flag = "";
            if (!collapsed && dr.undelivered > 0) {
                flag = "  ◄ PRIMER COLAPSO";
                collapsed = true;
            } else if (collapsed) {
                flag = "  ✗";
            }

            System.out.printf(
                    "║  %-12s ║ %5d ║ %5d  ║ %5d  ║ %5d  ║ %8d ║ %8.1f ║ %6d ms  ║%s%n",
                    dateStr, dr.total, dr.onTime, dr.late, dr.undelivered,
                    dr.alnsRuns, dr.score, dr.totalAlnsMs, flag);

            Thread.sleep(300); // espera que los daemon threads terminen entre días
        }

        System.out.println("╚══════════════╩═══════╩════════╩════════╩════════╩══════════╩══════════╩════════════╝");
        if (!collapsed) {
            System.out.println("[SCAN] Sin colapso detectado en los " + SCAN_DAYS + " días escaneados.");
        }
    }

    // ══ SIMULACIÓN EVENT-DRIVEN DE UN DÍA ════════════════════════════════════

    /**
     * Corre la simulación completa de un día a alta velocidad.
     * Retorna null si no hay datos para ese día.
     */
    private static DayResult runDay(String dateStr,
                                    Map<String, AirportDataDTO> airports,
                                    List<FlightScheduleDataDTO> flights,
                                    DeliveryTypeValues deliveryTypes,
                                    Path shipmentDir,
                                    double speedFactor,
                                    boolean verbose) throws Exception {
        Instant simStart = Instant.parse(dateStr + "T00:00:00Z");
        Instant simEnd   = simStart.plus(1, ChronoUnit.DAYS);

        // Grafo fresco por cada día (stateful)
        SpaceTimeGraph graph = new SpaceTimeGraph();
        airports.values().forEach(graph::addAirport);
        flights.forEach(graph::addScheduledFlight);

        // Publisher que intercepta entregas para medir puntualidad
        Map<String, Instant> deliverySimTimes = new ConcurrentHashMap<>();
        StatePublisher publisher = dto -> {
            if (dto instanceof BaggageDeliveredDTO d) {
                deliverySimTimes.put(d.baggageId(), d.simTime());
            }
        };

        SimulationConfig config = SimulationConfig.defaultTxt(simStart, simEnd, speedFactor);
        SimulationRunner runner = new SimulationRunner(graph, config, publisher);
        SimulationClock  clock  = runner.getClock();
        runner.init();

        // Wrapper del ALNS que mide el tiempo real de cómputo puro
        AtomicLong    totalAlnsNs = new AtomicLong(0);
        AtomicInteger alnsRuns    = new AtomicInteger(0);
        ALNSAlgorithm baseAlns    = new ALNSAlgorithm();
        RoutingOptimizer timedAlns = projection -> {
            long t0 = System.nanoTime();
            var result = baseAlns.optimize(projection);
            totalAlnsNs.addAndGet(System.nanoTime() - t0);
            alnsRuns.incrementAndGet();
            return result;
        };

        Thread shipmentThread = new Thread(new ShipmentInjectorThread(runner, clock,
                new TxtShipmentFeed(shipmentDir, airports, deliveryTypes), simStart, simEnd),
                "bench-shipment");
        shipmentThread.setDaemon(true);

        Path cancellationFile = shipmentDir.getParent().resolve("cancelaciones.txt");
        Thread cancellationThread = new Thread(new CancellationInjectorThread(runner, clock,
                new TxtCancellationFeed(cancellationFile, airports), simStart, simEnd),
                "bench-cancellation");
        cancellationThread.setDaemon(true);

        Thread alnsThread = new Thread(
                new AlnsThread(runner, timedAlns, clock, config, true), "bench-alns");
        alnsThread.setDaemon(true);

        Thread simThread = new Thread(runner, "bench-sim");
        simThread.setDaemon(false);

        if (verbose) {
            double lagSimMin = 350.0 * speedFactor / 1000.0 / 60.0;
            System.out.printf("%n[BENCH] Simulando %s  (x%.0f → ~%.0f s reales | lag ALNS ≈ %.1f min sim)%n",
                    dateStr, speedFactor, 86400.0 / speedFactor, lagSimMin);
        }

        shipmentThread.start();
        cancellationThread.start();
        alnsThread.start();
        simThread.start();
        simThread.join();

        if (graph.getAllBaggages().isEmpty()) return null;

        // ── Clasificar resultados ─────────────────────────────────────────────
        Set<String> deliveredIds = deliverySimTimes.keySet();

        List<BaggageRow> onTimeRows  = new ArrayList<>();
        List<BaggageRow> lateRows    = new ArrayList<>();
        List<BaggageRow> undelivRows = new ArrayList<>();

        for (Baggage b : graph.getAllBaggages()) {
            String  origin   = b.getShipment().getShipmentData().getOriginAirport().getIcao();
            Instant deadline = b.getDeadlineUtc();

            if (deliveredIds.contains(b.getId())) {
                Instant delivery  = deliverySimTimes.get(b.getId());
                boolean isLate    = delivery.isAfter(deadline);
                Duration lateness = isLate ? Duration.between(deadline, delivery) : Duration.ZERO;
                BaggageRow row = new BaggageRow(b.getId(), origin, b.getDestIcao(),
                        deadline, delivery, isLate, lateness);
                if (isLate) lateRows.add(row);
                else        onTimeRows.add(row);
            } else {
                undelivRows.add(new BaggageRow(b.getId(), origin, b.getDestIcao(),
                        deadline, null, false, Duration.ZERO));
            }
        }

        int total = onTimeRows.size() + lateRows.size() + undelivRows.size();

        double score = lateRows.stream()
                .mapToDouble(r -> Duration.between(r.deadline, r.arrival).toSeconds() / 3600.0)
                .sum()
                + undelivRows.size() * 1000.0;

        long totalAlnsMs = totalAlnsNs.get() / 1_000_000;

        if (verbose) {
            printReport(dateStr, total, onTimeRows, lateRows, undelivRows,
                    score, alnsRuns.get(), totalAlnsMs, runner);
        }

        return new DayResult(total, onTimeRows.size(), lateRows.size(),
                undelivRows.size(), score, alnsRuns.get(), totalAlnsMs);
    }

    // ══ REPORTE DETALLADO ════════════════════════════════════════════════════

    private static void printReport(String dateStr, int total,
                                    List<BaggageRow> onTime, List<BaggageRow> late,
                                    List<BaggageRow> undeliv,
                                    double score, int alnsRuns, long totalAlnsMs,
                                    SimulationRunner runner) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.printf ("║  ALNS BENCHMARK — %-44s  ║%n", dateStr);
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf ("║  ALNS: %4d ejecuciones  |  t.cómputo total: %6d ms          ║%n",
                alnsRuns, totalAlnsMs);
        System.out.printf ("║  t.promedio por ejecución: %3d ms                              ║%n",
                alnsRuns > 0 ? totalAlnsMs / alnsRuns : 0);
        System.out.printf ("║  Propuestas: %4d  |  Obsoletas: %4d  |  Aplicadas: %4d      ║%n",
                runner.getTotalProposed(), runner.getTotalStale(), runner.getTotalApplied());
        System.out.printf ("║  Score equivalente ALNS: %10.2f                          ║%n", score);
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf ("║  Maletas totales        : %5d                                  ║%n", total);
        System.out.printf ("║  Entregadas a tiempo    : %5d  (%5.1f%%)                       ║%n",
                onTime.size(), pct(onTime.size(), total));
        System.out.printf ("║  Entregadas con retraso : %5d  (%5.1f%%)                       ║%n",
                late.size(), pct(late.size(), total));
        System.out.printf ("║  No entregadas          : %5d  (%5.1f%%)                       ║%n",
                undeliv.size(), pct(undeliv.size(), total));
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");

        if (!late.isEmpty()) {
            System.out.println();
            System.out.println("══ ENTREGADAS FUERA DE PLAZO (" + late.size() + ") ═════════════════════════════════════");
            printHeader();
            late.stream()
                    .sorted(Comparator.comparing((BaggageRow r) -> r.lateness).reversed())
                    .forEach(AlnsBenchmarkMain::printRow);
        }

        if (!undeliv.isEmpty()) {
            System.out.println();
            System.out.println("══ NO ENTREGADAS (" + undeliv.size() + ") ═══════════════════════════════════════════════");
            System.out.printf("%-24s  %-6s  %-6s  %-16s%n", "ID", "Orig", "Dest", "Deadline(UTC)");
            System.out.println("─".repeat(58));
            undeliv.forEach(r -> System.out.printf("%-24s  %-6s  %-6s  %s%n",
                    r.id, r.origin, r.dest, FMT.format(r.deadline)));
        }

        if (!onTime.isEmpty()) {
            System.out.println();
            System.out.println("══ ENTREGADAS A TIEMPO (" + onTime.size() + ") ══════════════════════════════════════════");
            printHeader();
            onTime.forEach(AlnsBenchmarkMain::printRow);
        }
    }

    // ── Tipos y helpers ───────────────────────────────────────────────────────

    private record DayResult(int total, int onTime, int late, int undelivered,
                             double score, int alnsRuns, long totalAlnsMs) {}

    private record BaggageRow(String id, String origin, String dest,
                               Instant deadline, Instant arrival,
                               boolean isLate, Duration lateness) {}

    private static void printHeader() {
        System.out.printf("%-24s  %-6s  %-6s  %-16s  %-16s  %s%n",
                "ID", "Orig", "Dest", "Deadline(UTC)", "Entrega(UTC)", "Retraso");
        System.out.println("─".repeat(90));
    }

    private static void printRow(BaggageRow r) {
        System.out.printf("%-24s  %-6s  %-6s  %-16s  %-16s  %s%n",
                r.id, r.origin, r.dest,
                FMT.format(r.deadline),
                r.arrival != null ? FMT.format(r.arrival) : "—",
                r.isLate ? "+" + formatDuration(r.lateness) : "OK");
    }

    private static double pct(int part, int total) {
        return total == 0 ? 0.0 : 100.0 * part / total;
    }

    private static String formatDuration(Duration d) {
        long h = d.toHours();
        long m = d.toMinutesPart();
        return String.format("%dh%02dm", h, m);
    }

    private static Path resourcePath(String name) throws Exception {
        URI uri = AlnsBenchmarkMain.class.getClassLoader().getResource(name).toURI();
        return Paths.get(uri);
    }
}
