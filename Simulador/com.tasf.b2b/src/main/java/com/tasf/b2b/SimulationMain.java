package com.tasf.b2b;

import com.tasf.b2b.domain.model.graph.SpaceTimeGraph;
import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;
import com.tasf.b2b.domain.model.graph.immovable.DeliveryTypeValues;
import com.tasf.b2b.domain.model.graph.immovable.FlightScheduleDataDTO;
import com.tasf.b2b.domain.optimizer.alns.ALNSAlgorithm;
import com.tasf.b2b.domain.optimizer.genetic.GeneticAlgorithm;
import com.tasf.b2b.domain.simulator.thread.AlnsThread;
import com.tasf.b2b.domain.simulator.thread.GeneticThread;
import com.tasf.b2b.domain.simulator.thread.CancellationInjectorThread;
import com.tasf.b2b.domain.simulator.thread.ShipmentInjectorThread;
import com.tasf.b2b.domain.simulator.SimulationClock;
import com.tasf.b2b.domain.simulator.SimulationConfig;
import com.tasf.b2b.domain.simulator.SimulationRunner;
import com.tasf.b2b.domain.simulator.StatePublisher;
import com.tasf.b2b.infrastructure.files.AirportParser;
import com.tasf.b2b.infrastructure.files.FlightParser;
import com.tasf.b2b.infrastructure.files.TxtCancellationFeed;
import com.tasf.b2b.infrastructure.files.TxtShipmentFeed;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Punto de entrada principal de la simulación.
 *
 * Carga de aeropuertos y vuelos: una sola vez al inicio.
 * Inyección de envíos y cancelaciones: en tiempo real vía hilos dedicados.
 *
 * Ajusta SPEED_FACTOR para cambiar la velocidad:
 *   x1       → duración real igual a la simulada
 *   x86400   → 1 día simulado por segundo real  (~3 s para 3 días)
 *   x2       → la simulación dura la mitad del tiempo simulado
 */
public class SimulationMain {

    private static final Instant SIM_START    = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant SIM_END      = Instant.parse("2026-01-02T12:00:00Z"); // medio día
    private static final double  SPEED_FACTOR = 80.0;

    public static void main(String[] args) throws Exception {

        // ── 1. Carga inicial de aeropuertos (una sola vez) ────────────────────
        Path airportsFile = resourcePath("c.1inf54.26.1.v1.Aeropuerto.husos.v1.20250818__estudiantes.txt");
        Map<String, AirportDataDTO> airports = AirportParser.parse(airportsFile);
        System.out.println("[INIT] Aeropuertos cargados: " + airports.size());

        // ── 2. Carga inicial de vuelos (una sola vez) ─────────────────────────
        Path flightsFile = resourcePath("planes_vuelo.txt");
        List<FlightScheduleDataDTO> flights = FlightParser.parse(flightsFile, airports);
        System.out.println("[INIT] Programaciones de vuelo cargadas: " + flights.size());

        // ── 3. Construcción del grafo ─────────────────────────────────────────
        DeliveryTypeValues deliveryTypes = new DeliveryTypeValues();
        SpaceTimeGraph graph = new SpaceTimeGraph();

        airports.values().forEach(graph::addAirport);
        flights.forEach(graph::addScheduledFlight);

        // ── 4. Config, runner y reloj ─────────────────────────────────────────
        SimulationConfig config    = SimulationConfig.defaultTxt(SIM_START, SIM_END, SPEED_FACTOR);
        StatePublisher   publisher = dto -> {};  // no-op hasta tener RedisStatePublisher
        SimulationRunner runner    = new SimulationRunner(graph, config, publisher);
        SimulationClock  clock     = runner.getClock();
        runner.init();

        // ── 5. Hilo de inyección de envíos (carga constante por tiempo) ───────
        Path shipmentDir = resourcePath("_envios_preliminar_").getParent()
                .resolve("_envios_preliminar_");
        Thread shipmentThread = new Thread(
                new ShipmentInjectorThread(runner, clock,
                        new TxtShipmentFeed(shipmentDir, airports, deliveryTypes),
                        SIM_START, SIM_END),
                "shipment-injector");
        shipmentThread.setDaemon(true);

        // ── 6. Hilo de cancelaciones (stub — archivo vacío por ahora) ─────────
        Path cancellationFile = shipmentDir.getParent().resolve("cancelaciones.txt");
        Thread cancellationThread = new Thread(
                new CancellationInjectorThread(runner, clock,
                        new TxtCancellationFeed(cancellationFile, airports),
                        SIM_START, SIM_END),
                "cancellation-injector");
        cancellationThread.setDaemon(true);

        // ── 7. Hilo(s) de optimización según OptimizerMode ────────────────────
        List<Thread> optimizerThreads = new ArrayList<>();
        switch (config.optimizerMode()) {
            case ALNS_ONLY -> optimizerThreads.add(new Thread(
                    new AlnsThread(runner, new ALNSAlgorithm(), clock, config, true), "alns-thread"));
            case GENETIC_ONLY -> optimizerThreads.add(new Thread(
                    new GeneticThread(runner, new GeneticAlgorithm(), clock, true), "genetic-thread"));
            case ALNS_ACTIVE_GENETIC_EVAL -> {
                optimizerThreads.add(new Thread(
                        new AlnsThread(runner, new ALNSAlgorithm(), clock, config, true), "alns-thread"));
                optimizerThreads.add(new Thread(
                        new GeneticThread(runner, new GeneticAlgorithm(), clock, false), "genetic-eval-thread"));
            }
            case GENETIC_ACTIVE_ALNS_EVAL -> {
                optimizerThreads.add(new Thread(
                        new GeneticThread(runner, new GeneticAlgorithm(), clock, true), "genetic-thread"));
                optimizerThreads.add(new Thread(
                        new AlnsThread(runner, new ALNSAlgorithm(), clock, config, false), "alns-eval-thread"));
            }
        }
        optimizerThreads.forEach(t -> t.setDaemon(true));

        // ── 8. Arranque ───────────────────────────────────────────────────────
        Thread simThread = new Thread(runner, "simulation-runner");
        simThread.setDaemon(false);

        shipmentThread.start();
        cancellationThread.start();
        optimizerThreads.forEach(Thread::start);
        simThread.start();

        System.out.printf("[MAIN] Simulación iniciada (x%.0f) — %s → %s%n",
                SPEED_FACTOR, SIM_START, SIM_END);

        // ── 9. Demo de pausa (a los 500ms reales ≈ 12h simuladas) ────────────
        Thread.sleep(500);
        System.out.println("[MAIN] ⏸  Pausa");
        runner.pause();

        Thread.sleep(1_000);
        System.out.println("[MAIN] ▶  Reanudando");
        runner.resume();

        // ── 10. Esperar fin ───────────────────────────────────────────────────
        simThread.join();

        int delivered = runner.getDeliveredBaggages().size();
        int pending   = graph.getPendingBaggages().size();
        int assigned  = graph.getAssignedBaggages().size();
        int total     = graph.getAllBaggages().size();

        int proposed  = runner.getTotalProposed();
        int stale     = runner.getTotalStale();
        int applied   = runner.getTotalApplied();

        System.out.println("[MAIN] ════════════════════════════════════════");
        System.out.println("[MAIN] Simulación finalizada.");
        System.out.printf ("[MAIN]   Maletas totales   : %d%n", total);
        System.out.printf ("[MAIN]   Entregadas        : %d (%.1f%%)%n", delivered, pct(delivered, total));
        System.out.printf ("[MAIN]   Asignadas (stuck) : %d (%.1f%%)%n", assigned,  pct(assigned,  total));
        System.out.printf ("[MAIN]   Pendientes        : %d (%.1f%%)%n", pending,   pct(pending,   total));
        System.out.println("[MAIN] ────────────────────────────────────────");
        System.out.printf ("[MAIN]   Soluciones ALNS   : %d%n", runner.getSolutionCount());
        System.out.printf ("[MAIN]   Rutas propuestas  : %d%n", proposed);
        System.out.printf ("[MAIN]   Rutas obsoletas   : %d (%.1f%%)%n", stale,   pct(stale,   proposed));
        System.out.printf ("[MAIN]   Rutas aplicadas   : %d (%.1f%%)%n", applied, pct(applied, proposed));
        System.out.println("[MAIN] ════════════════════════════════════════");
    }

    private static double pct(int part, int total) {
        return total == 0 ? 0.0 : 100.0 * part / total;
    }

    /** Resuelve un recurso del classpath a Path (funciona tanto en IDE como en JAR). */
    private static Path resourcePath(String name) throws Exception {
        URI uri = SimulationMain.class.getClassLoader().getResource(name).toURI();
        return Paths.get(uri);
    }
}
