package com.tasf.b2b.application.usecase;

import com.tasf.b2b.application.port.in.DisruptionCommand;
import com.tasf.b2b.application.port.in.SimulationControlPort;
import com.tasf.b2b.domain.model.graph.SpaceTimeGraph;
import com.tasf.b2b.domain.model.graph.componentsgraph.FlightEdge;
import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;
import com.tasf.b2b.domain.model.graph.immovable.FlightScheduleDataDTO;
import com.tasf.b2b.domain.simulator.event.FlightCancelledEvent;
import com.tasf.b2b.domain.optimizer.alns.ALNSAlgorithm;
import com.tasf.b2b.domain.optimizer.genetic.GeneticAlgorithm;
import com.tasf.b2b.domain.simulator.SimulationClock;
import com.tasf.b2b.domain.simulator.SimulationConfig;
import com.tasf.b2b.domain.simulator.SimulationRunner;
import com.tasf.b2b.domain.simulator.StatePublisher;

import com.tasf.b2b.domain.simulator.feed.CancellationFeed;
import com.tasf.b2b.domain.simulator.feed.ShipmentFeed;
import com.tasf.b2b.domain.simulator.thread.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Caso de uso principal: orquesta el arranque de una sesión de simulación.
 *
 * Responsabilidades:
 *  1. Cargar aeropuertos y vuelos desde archivos (una sola vez, en el constructor).
 *  2. Al llamar start(): calcular speedFactor, construir el grafo, instanciar feeds,
 *     crear todos los hilos y arrancarlos.
 *  3. Gestionar el ciclo de vida (pause, resume, stop) delegando en el runner y el registry.
 *
 * Por qué la carga de archivos está en el constructor:
 *  Aeropuertos y vuelos son los mismos para todas las sesiones (mismos archivos).
 *  Cargarlos una sola vez evita repetir la lectura de disco en cada start().
 *  Son inmutables después de la carga, así que compartirlos entre sesiones es seguro.
 *
 * Cuando llegue Spring Boot:
 *  - Anotar con @Service
 *  - Inyectar paths via @Value("${simulation.airports-file}") etc.
 *  - El constructor se convierte en @Bean o @PostConstruct
 */
public class RunSimulationUseCase implements SimulationControlPort {

    /**
     * Tiempo real objetivo para correr la simulación.
     * Ejemplo: 5 días simulados → el reloj va 480x más rápido → termina en ~15 min reales.
     * Ajustar si se quiere una demo más rápida o más lenta.
     */
    private static final long TARGET_REAL_SECONDS = 15 * 60L; // 15 minutos

    private final SimulationRegistry          registry;
    private final Function<String, StatePublisher> publisherFactory;
    private final Map<String, AirportDataDTO>      airports;
    private final List<FlightScheduleDataDTO>      flights;
    private final BiFunction<Instant, Instant, ShipmentFeed>     shipmentFeedFactory;
    private final BiFunction<Instant, Instant, CancellationFeed> cancellationFeedFactory;

    /**
     * @param registry                registro compartido de sesiones activas
     * @param airports                mapa ICAO → AirportDataDTO cargado desde BD
     * @param flights                 lista de vuelos recurrentes cargados desde BD
     * @param shipmentFeedFactory     factory (simStart, simEnd) → ShipmentFeed acotado al rango.
     *                                Recibe el rango para que la implementación de BD cargue solo
     *                                los envíos del periodo simulado (cargar la tabla completa
     *                                agota el heap con meses de datos).
     * @param cancellationFeedFactory factory (simStart, simEnd) → CancellationFeed acotado al rango
     * @param publisherFactory        factory que recibe un sessionId y devuelve un StatePublisher
     */
    public RunSimulationUseCase(SimulationRegistry registry,
                                Map<String, AirportDataDTO> airports,
                                List<FlightScheduleDataDTO> flights,
                                BiFunction<Instant, Instant, ShipmentFeed> shipmentFeedFactory,
                                BiFunction<Instant, Instant, CancellationFeed> cancellationFeedFactory,
                                Function<String, StatePublisher> publisherFactory) {
        this.registry                = registry;
        this.airports                = airports;
        this.flights                 = flights;
        this.shipmentFeedFactory     = shipmentFeedFactory;
        this.cancellationFeedFactory = cancellationFeedFactory;
        this.publisherFactory        = publisherFactory;
    }

    public Map<String, AirportDataDTO> getAirports() { return airports; }
    public List<FlightScheduleDataDTO> getFlights()  { return flights;  }

    // ── start ─────────────────────────────────────────────────────────────────

    /**
     * Crea e inicia una sesión de simulación para el rango de fechas indicado.
     *
     * El speedFactor se calcula automáticamente para que la simulación dure
     * aproximadamente TARGET_REAL_SECONDS segundos de reloj real.
     *
     * @return UUID de la sesión — usar en pause/resume/stop/query
     */
    @Override
    public String start(Instant simStart, Instant simEnd) {

        // ── 1. speedFactor ────────────────────────────────────────────────────
        // speedFactor = segundos_simulados / segundos_reales_objetivo
        // Ejemplo: 5 días (432 000 s) / 900 s = 480x
        long   simSeconds   = Duration.between(simStart, simEnd).toSeconds();
        double speedFactor  = Math.max(1.0, (double) simSeconds / TARGET_REAL_SECONDS);
        System.out.printf("[SIM] speedFactor calculado: %.1fx%n", speedFactor);

        // ── 2. Grafo espacio-temporal ─────────────────────────────────────────
        // Cada sesión tiene su propio grafo — no se comparten entre sesiones.
        SpaceTimeGraph graph = new SpaceTimeGraph();
        airports.values().forEach(graph::addAirport);
        flights.forEach(graph::addScheduledFlight);

        // ── 3. Sesión ID + publisher por sesión ───────────────────────────────
        String         sessionId = UUID.randomUUID().toString();
        StatePublisher publisher = publisherFactory.apply(sessionId);

        // ── 4. Configuración y runner ─────────────────────────────────────────
        SimulationConfig config = SimulationConfig.defaultTxt(simStart, simEnd, speedFactor);
        SimulationRunner runner = new SimulationRunner(graph, config, publisher);
        runner.init(); // siembra HorizonExpandEvent y SimulationEndEvent en la cola

        SimulationClock clock = runner.getClock();

        // ── 5. Feeds de datos (acotados al rango simulado) ────────────────────
        ShipmentFeed     shipmentFeed     = shipmentFeedFactory.apply(simStart, simEnd);
        CancellationFeed cancellationFeed = cancellationFeedFactory.apply(simStart, simEnd);

        // ── 6. Sesión ─────────────────────────────────────────────────────────
        SimulationSession session = new SimulationSession(sessionId, runner, graph, config, publisher);

        // ── 7. Hilo del runner (no-daemon) ────────────────────────────────────
        // Es el único hilo no-daemon: la JVM espera a que termine.
        // El wrapper actualiza el status de la sesión cuando el runner finaliza.
        Thread simThread = new Thread(() -> {
            session.setStatus(SimulationSession.SimStatus.RUNNING);
            runner.run();
            // Si llegamos aquí, el SimulationEndEvent disparó y el loop terminó
            if (session.getStatus() != SimulationSession.SimStatus.STOPPED) {
                session.setStatus(SimulationSession.SimStatus.COMPLETED);
            }
            registry.remove(sessionId); // liberar recursos del registry al terminar
        }, "simulation-runner-" + sessionId);
        simThread.setDaemon(false);

        // ── 8. Hilos inyectores (daemon) ──────────────────────────────────────
        // Daemon = mueren solos si el simThread (no-daemon) termina.
        Thread shipmentThread = new Thread(
                new ShipmentInjectorThread(runner, clock, shipmentFeed, simStart, simEnd),
                "shipment-injector-" + sessionId);
        shipmentThread.setDaemon(true);

        Thread cancellationThread = new Thread(
                new CancellationInjectorThread(runner, clock, cancellationFeed, simStart, simEnd),
                "cancellation-injector-" + sessionId);
        cancellationThread.setDaemon(true);

        // ── 9. Hilos de optimización (daemon) ─────────────────────────────────
        // isActive=true → el hilo envía RouteSolutionEvent al runner (afecta la simulación)
        // isActive=false → solo evalúa métricas, no modifica nada (modo comparación)
        List<Thread> optimizerThreads = buildOptimizerThreads(runner, clock, config, sessionId);
        optimizerThreads.forEach(t -> t.setDaemon(true));

        // ── 10. Registrar todos los hilos en la sesión para poder pararlos ────
        List<Thread> allThreads = new ArrayList<>();
        allThreads.add(simThread);
        allThreads.add(shipmentThread);
        allThreads.add(cancellationThread);
        allThreads.addAll(optimizerThreads);
        session.setAllThreads(allThreads);

        // ── 11. Registrar la sesión ANTES de arrancar hilos ──────────────────
        // Si alguien hace GET /simulations/:id justo después del POST, debe encontrarla.
        registry.register(session);

        // ── 12. Arranque ──────────────────────────────────────────────────────
        shipmentThread.start();
        cancellationThread.start();
        optimizerThreads.forEach(Thread::start);
        simThread.start(); // último: el grafo ya tiene vuelos en cola antes de que ALNS empiece

        System.out.printf("[SIM] Sesión %s iniciada: %s → %s (x%.1f)%n",
                sessionId, simStart, simEnd, speedFactor);

        return sessionId;
    }

    // ── pause / resume / stop ─────────────────────────────────────────────────

    @Override
    public void pause(String sessionId) {
        SimulationSession session = registry.findOrThrow(sessionId);
        if (session.getStatus() != SimulationSession.SimStatus.RUNNING) {
            throw new IllegalStateException("Solo se puede pausar una sesión en estado running");
        }
        session.getRunner().pause();
        session.setStatus(SimulationSession.SimStatus.PAUSED);
    }

    @Override
    public void resume(String sessionId) {
        SimulationSession session = registry.findOrThrow(sessionId);
        if (session.getStatus() != SimulationSession.SimStatus.PAUSED) {
            throw new IllegalStateException("Solo se puede reanudar una sesión en estado paused");
        }
        session.getRunner().resume();
        session.setStatus(SimulationSession.SimStatus.RUNNING);
    }

    @Override
    public void stop(String sessionId) {
        SimulationSession session = registry.findOrThrow(sessionId);
        // interruptAll() marca el status como STOPPED e interrumpe todos los hilos.
        // El simThread captura la InterruptedException y pone runner.running=false.
        session.interruptAll();
        registry.remove(sessionId);
        System.out.printf("[SIM] Sesión %s detenida manualmente.%n", sessionId);
    }

    // ── injectDisruption ──────────────────────────────────────────────────────

    @Override
    public List<String> injectDisruption(String sessionId, DisruptionCommand cmd) {
        SimulationSession session = registry.findOrThrow(sessionId);
        SimulationRunner  runner  = session.getRunner();
        SpaceTimeGraph    graph   = session.getGraph();
        SimulationClock   clock   = runner.getClock();

        // Selección de los FlightEdge objetivo. Solo lectura del grafo (se acepta la
        // inconsistencia de microsegundos); la mutación ocurre en el hilo del runner
        // vía runner.submit(FlightCancelledEvent), que es thread-safe.
        List<FlightEdge> targets = new ArrayList<>();
        for (FlightEdge fe : graph.getAllFlightEdges()) {
            if (fe.isCancelled()) continue;
            boolean match = switch (cmd.kind()) {
                case CANCELLATION, AVERIA ->
                        cmd.flightId() != null && fe.getIdFlightEdge().equals(cmd.flightId());
                case SEGMENT_BLOCK ->
                        fe.getFromNode().getIcao().equals(cmd.originIcao())
                                && fe.getToNode().getIcao().equals(cmd.destIcao())
                                && inWindow(fe.getFromNode().getTimeUtc(), cmd.fromUtc(), cmd.toUtc());
                case NODE_BLOCK ->
                        fe.getFromNode().getIcao().equals(cmd.originIcao())
                                && inWindow(fe.getFromNode().getTimeUtc(), cmd.fromUtc(), cmd.toUtc());
            };
            if (match) targets.add(fe);
        }

        List<String> affected = new ArrayList<>();
        for (FlightEdge fe : targets) {
            // simTime = ahora → la cancelación se aplica de inmediato.
            // depTimeUtc = salida real del vuelo → clave para SpaceTimeGraph.cancelFlight.
            runner.submit(new FlightCancelledEvent(
                    clock.now(),
                    fe.getFlightScheduleData().getId(),
                    fe.getFromNode().getTimeUtc(),
                    clock));
            affected.add(fe.getIdFlightEdge());
        }

        System.out.printf("[SIM] Disrupción %s en sesión %s → %d vuelo(s) afectado(s)%n",
                cmd.kind(), sessionId, affected.size());
        return affected;
    }

    private static boolean inWindow(Instant t, Instant from, Instant to) {
        if (from != null && t.isBefore(from)) return false;
        if (to != null && t.isAfter(to)) return false;
        return true;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<Thread> buildOptimizerThreads(SimulationRunner runner,
                                               SimulationClock clock,
                                               SimulationConfig config,
                                               String sessionId) {
        List<Thread> threads = new ArrayList<>();
        switch (config.optimizerMode()) {
            case ALNS_ONLY ->
                threads.add(new Thread(
                        new AlnsThread(runner, new ALNSAlgorithm(), clock, config, true),
                        "alns-" + sessionId));
            case GENETIC_ONLY ->
                threads.add(new Thread(
                        new GeneticThread(runner, new GeneticAlgorithm(), clock, true),
                        "genetic-" + sessionId));
            case ALNS_ACTIVE_GENETIC_EVAL -> {
                // ALNS activo: sus rutas se aplican al grafo
                threads.add(new Thread(
                        new AlnsThread(runner, new ALNSAlgorithm(), clock, config, true),
                        "alns-" + sessionId));
                // Genético en evaluación: corre en paralelo pero no aplica sus rutas
                threads.add(new Thread(
                        new GeneticThread(runner, new GeneticAlgorithm(), clock, false),
                        "genetic-eval-" + sessionId));
            }
            case GENETIC_ACTIVE_ALNS_EVAL -> {
                threads.add(new Thread(
                        new GeneticThread(runner, new GeneticAlgorithm(), clock, true),
                        "genetic-" + sessionId));
                threads.add(new Thread(
                        new AlnsThread(runner, new ALNSAlgorithm(), clock, config, false),
                        "alns-eval-" + sessionId));
            }
        }
        return threads;
    }
}
