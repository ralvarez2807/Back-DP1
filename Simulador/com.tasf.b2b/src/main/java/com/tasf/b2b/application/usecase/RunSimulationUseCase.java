package com.tasf.b2b.application.usecase;

import com.tasf.b2b.application.dto.BaggageRouteView;
import com.tasf.b2b.application.dto.FinishedSessionView;
import com.tasf.b2b.application.port.in.DisruptionCommand;
import com.tasf.b2b.application.port.in.InjectShipmentCommand;
import com.tasf.b2b.application.port.in.InjectShipmentResult;
import com.tasf.b2b.application.port.in.SimulationControlPort;
import com.tasf.b2b.application.port.in.StartSimulationCommand;
import com.tasf.b2b.domain.model.graph.SpaceTimeGraph;
import com.tasf.b2b.domain.model.graph.componentsgraph.FlightEdge;
import com.tasf.b2b.domain.model.graph.immovable.AirportDataDTO;
import com.tasf.b2b.domain.model.graph.immovable.DeliveryTypeValues;
import com.tasf.b2b.domain.model.graph.immovable.FlightScheduleDataDTO;
import com.tasf.b2b.domain.model.graph.immovable.ShipmentDataDTO;
import com.tasf.b2b.domain.simulator.event.FlightCancelledEvent;
import com.tasf.b2b.domain.simulator.event.NewShipmentEvent;
import com.tasf.b2b.domain.optimizer.alns.ALNSAlgorithm;
import com.tasf.b2b.domain.optimizer.genetic.GeneticAlgorithm;
import com.tasf.b2b.domain.simulator.SimulationClock;
import com.tasf.b2b.domain.simulator.SimulationConfig;
import com.tasf.b2b.domain.optimizer.metrics.OptimizerPublisher;
import com.tasf.b2b.domain.simulator.SimulationRunner;
import com.tasf.b2b.domain.simulator.StatePublisher;

import com.tasf.b2b.domain.simulator.feed.CancellationFeed;
import com.tasf.b2b.domain.simulator.feed.ShipmentFeed;
import com.tasf.b2b.domain.simulator.thread.*;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
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
     * Usuario sintético propietario de la sesión de "Operación Día a Día".
     * No corresponde a ninguna cuenta real, por lo que no choca con el límite de
     * "una sesión activa por usuario" de los usuarios humanos ni aparece en
     * GET /simulations/mine de nadie.
     */
    public static final String DAILY_OPS_USER = "__daily_ops__";

    private final SimulationRegistry               registry;
    private final Function<String, StatePublisher>    publisherFactory;
    private final Function<String, OptimizerPublisher> optimizerPublisherFactory;
    private final Map<String, AirportDataDTO>          airports;
    private final List<FlightScheduleDataDTO>      flights;
    private final DeliveryTypeValues               deliveryTypes;
    private final BiFunction<Instant, Instant, ShipmentFeed>     shipmentFeedFactory;
    private final BiFunction<Instant, Instant, CancellationFeed> cancellationFeedFactory;
    private final QuerySimulationUseCase           query;
    private final FinishedSessionCache             finishedSessionCache;

    /** Secuencia para los ids de envíos cargados manualmente (operario). */
    private final AtomicLong manualShipmentSeq = new AtomicLong(0);

    private static final DateTimeFormatter MANUAL_ID_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    /** Feeds vacíos para la Operación Día a Día: sin envíos ni cancelaciones simulados. */
    private static final ShipmentFeed     EMPTY_SHIPMENT_FEED     = () -> null;
    private static final CancellationFeed EMPTY_CANCELLATION_FEED = () -> null;

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
                                DeliveryTypeValues deliveryTypes,
                                BiFunction<Instant, Instant, ShipmentFeed> shipmentFeedFactory,
                                BiFunction<Instant, Instant, CancellationFeed> cancellationFeedFactory,
                                Function<String, StatePublisher> publisherFactory,
                                Function<String, OptimizerPublisher> optimizerPublisherFactory,
                                QuerySimulationUseCase query,
                                FinishedSessionCache finishedSessionCache) {
        this.registry                   = registry;
        this.airports                   = airports;
        this.flights                    = flights;
        this.deliveryTypes              = deliveryTypes;
        this.shipmentFeedFactory        = shipmentFeedFactory;
        this.cancellationFeedFactory    = cancellationFeedFactory;
        this.publisherFactory           = publisherFactory;
        this.optimizerPublisherFactory  = optimizerPublisherFactory;
        this.query                      = query;
        this.finishedSessionCache       = finishedSessionCache;
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
    public String start(StartSimulationCommand cmd) {
        // Simulación manual de usuario: consume los datos simulados (BD/archivo).
        return startSession(cmd, true);
    }

    /**
     * Núcleo de arranque compartido por la simulación manual y la Operación Día a Día.
     *
     * @param useSimulatedFeeds {@code true} → inyecta envíos y cancelaciones simulados
     *        (BD/archivo); {@code false} → arranca sin datos simulados (solo la red de
     *        vuelos), de modo que las órdenes provienen únicamente de cargas reales
     *        (manuales o por archivo) vía {@link #injectShipment} y las circunstancias
     *        vía {@link #injectDisruption}. Es lo que usa la Operación Día a Día.
     */
    private String startSession(StartSimulationCommand cmd, boolean useSimulatedFeeds) {
        validateCombination(cmd);

        if (registry.hasActiveSession(cmd.username()))
            throw new IllegalStateException(
                    "El usuario ya tiene una simulación activa. Detén la sesión actual antes de iniciar una nueva.");

        Instant simStart    = cmd.simStart();
        Instant simEnd      = cmd.simEnd();
        double  speedFactor = cmd.speedFactor();

        // ── 2. Grafo espacio-temporal ─────────────────────────────────────────
        // Cada sesión tiene su propio grafo — no se comparten entre sesiones.
        SpaceTimeGraph graph = new SpaceTimeGraph();
        airports.values().forEach(graph::addAirport);
        flights.forEach(graph::addScheduledFlight);

        // ── 3. Sesión ID + publishers por sesión ──────────────────────────────
        String            sessionId          = UUID.randomUUID().toString();
        StatePublisher    publisher          = publisherFactory.apply(sessionId);
        OptimizerPublisher optimizerPublisher = optimizerPublisherFactory.apply(sessionId);

        // ── 4. Configuración y runner ─────────────────────────────────────────
        // minConnectionMinutes=15: tiempo muerto de escala — la maleta espera ≥15 min
        // en el aeropuerto de conexión antes de embarcar el siguiente vuelo (lo respeta
        // RouteFinder al planificar rutas con escalas).
        // pickupMinutes=15: al llegar a su destino final, la maleta espera 15 min en el
        // almacén hasta ser recogida por el pasajero (y descontada del total almacenado).
        SimulationConfig config = new SimulationConfig(
                cmd.solverTimingMode(), cmd.optimizerMode(), speedFactor,
                simStart, simEnd, cmd.dataSource(), 15, 15, cmd.collapseOnFailure());
        SimulationRunner runner = new SimulationRunner(graph, config, publisher);
        runner.init(); // siembra HorizonExpandEvent y SimulationEndEvent en la cola

        SimulationClock clock = runner.getClock();

        // ── 5. Feeds de datos ─────────────────────────────────────────────────
        // La Operación Día a Día (useSimulatedFeeds=false) NO consume envíos ni
        // cancelaciones simulados: sus aeropuertos arrancan vacíos y solo se llenan
        // con órdenes reales cargadas por el operario (manual o por archivo).
        ShipmentFeed     shipmentFeed     = useSimulatedFeeds
                ? shipmentFeedFactory.apply(simStart, simEnd)
                : EMPTY_SHIPMENT_FEED;
        CancellationFeed cancellationFeed = useSimulatedFeeds
                ? cancellationFeedFactory.apply(simStart, simEnd)
                : EMPTY_CANCELLATION_FEED;

        // ── 6. Sesión ─────────────────────────────────────────────────────────
        SimulationSession session = new SimulationSession(sessionId, cmd.username(), runner, graph, config, publisher, optimizerPublisher);

        // ── 7. Hilo del runner (no-daemon) ────────────────────────────────────
        // Es el único hilo no-daemon: la JVM espera a que termine.
        // El wrapper actualiza el status de la sesión cuando el runner finaliza.
        Thread simThread = new Thread(() -> {
            session.setStatus(SimulationSession.SimStatus.RUNNING);
            runner.run();
            // Si llegamos aquí, terminó por SimulationEndEvent, colapso, o stop().
            // stop() ya dejó el status en STOPPED y ya guardó el resultado final:
            // no lo pisamos ni lo guardamos de nuevo acá.
            if (session.getStatus() != SimulationSession.SimStatus.STOPPED) {
                String collapseReason = runner.getCollapseReason();
                SimulationSession.SimStatus finalStatus = collapseReason != null
                        ? SimulationSession.SimStatus.COLLAPSED
                        : SimulationSession.SimStatus.COMPLETED;
                session.setStatus(finalStatus);

                // Al colapsar, el estado actual de las maletas ya refleja la falla
                // (deadline vencido o maleta sin ruta). Se prefiere el último
                // snapshot bueno guardado por el hilo solution-snapshot (previo a
                // la falla) para que "assignedRoutes" siga siendo la última
                // planificación exitosa, no la que disparó el colapso.
                List<BaggageRouteView> assignedRoutes = finalStatus == SimulationSession.SimStatus.COLLAPSED
                        ? finishedSessionCache.peek(session.getId())
                                .map(FinishedSessionView::assignedRoutes)
                                .orElseGet(() -> query.buildAllRoutes(session)) // colapsó antes del primer snapshot
                        : query.buildAllRoutes(session);

                finishedSessionCache.record(new FinishedSessionView(
                        session.getId(), session.getUsername(), finalStatus.name(),
                        Instant.now(), collapseReason, assignedRoutes));
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

        // Snapshot periódico (reloj real, no de simulación) de la última solución
        // conocida. Así, si el proceso se corta de golpe (crash, kill -9) sin pasar
        // por el cierre prolijo de simThread ni por stop(), la última planificación
        // exitosa igual quedó guardada en finishedSessionCache (TTL 5 min) y se
        // puede pedir por GET /simulations/:id/result.
        Thread solutionSnapshotThread = new Thread(
                () -> runSolutionSnapshotLoop(session), "solution-snapshot-" + sessionId);
        solutionSnapshotThread.setDaemon(true);

        // ── 9. Hilos de optimización (daemon) ─────────────────────────────────
        // isActive=true → el hilo envía RouteSolutionEvent al runner (afecta la simulación)
        // isActive=false → solo evalúa métricas, no modifica nada (modo comparación)
        List<Thread> optimizerThreads = buildOptimizerThreads(runner, clock, config, sessionId, optimizerPublisher);
        optimizerThreads.forEach(t -> t.setDaemon(true));

        // ── 10. Registrar todos los hilos en la sesión para poder pararlos ────
        List<Thread> allThreads = new ArrayList<>();
        allThreads.add(simThread);
        allThreads.add(shipmentThread);
        allThreads.add(cancellationThread);
        allThreads.add(solutionSnapshotThread);
        allThreads.addAll(optimizerThreads);
        session.setAllThreads(allThreads);

        // ── 11. Registrar la sesión ANTES de arrancar hilos ──────────────────
        // Si alguien hace GET /simulations/:id justo después del POST, debe encontrarla.
        registry.register(session);

        // ── 12. Arranque ──────────────────────────────────────────────────────
        shipmentThread.start();
        cancellationThread.start();
        solutionSnapshotThread.start();
        optimizerThreads.forEach(Thread::start);
        simThread.start(); // último: el grafo ya tiene vuelos en cola antes de que ALNS empiece

        System.out.printf("[SIM] Sesión %s iniciada: %s → %s (x%.1f)%n",
                sessionId, simStart, simEnd, speedFactor);

        return sessionId;
    }

    // ── Snapshot periódico de la última solución ─────────────────────────────

    /** Cada cuánto (reloj real) se refresca la última solución conocida. */
    private static final long SOLUTION_SNAPSHOT_INTERVAL_MS = 3000;

    private void runSolutionSnapshotLoop(SimulationSession session) {
        SimulationRunner runner = session.getRunner();
        while (runner.isRunning()) {
            try {
                Thread.sleep(SOLUTION_SNAPSHOT_INTERVAL_MS);
                // Se re-chequea tras dormir: evita pisar con "RUNNING" el registro
                // definitivo (COMPLETED/COLLAPSED/STOPPED) si el runner terminó
                // justo mientras este hilo dormía.
                if (!runner.isRunning()) return;
                finishedSessionCache.record(new FinishedSessionView(
                        session.getId(), session.getUsername(), "RUNNING",
                        Instant.now(), null, query.buildAllRoutes(session)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    // ── Operación Día a Día ─────────────────────────────────────────────────────

    /**
     * Lanza la sesión de "Operación Día a Día": una corrida en tiempo real anclada
     * a la fecha real de hoy que refleja los planes de vuelo recurrentes del día.
     *
     * Reutiliza toda la maquinaria de start() (grafo, runner, publisher, hilos ALNS,
     * inyectores y WebSocket) con la combinación soportada DB + REAL_TIME + ALNS_ONLY,
     * pero con un usuario sintético propio para que conviva con las simulaciones
     * manuales de los usuarios sin interferir.
     *
     * El llamador (DailyOperationsService) garantiza idempotencia: solo invoca este
     * método cuando no hay ya una sesión día-a-día activa.
     *
     * @param simStart    instante de arranque (normalmente Instant.now())
     * @param simEnd      fin del horizonte (lejano; con rolling horizon la memoria es acotada)
     * @param speedFactor factor de velocidad (1.0 = tiempo real estricto)
     * @return UUID de la sesión creada
     */
    public String startDailyOperations(Instant simStart, Instant simEnd, double speedFactor) {
        StartSimulationCommand cmd = new StartSimulationCommand(
                DAILY_OPS_USER,
                SimulationConfig.DataSource.DB,
                SimulationConfig.SolverTimingMode.REAL_TIME,
                SimulationConfig.OptimizerMode.ALNS_ONLY,
                simStart, simEnd, speedFactor, false);
        // Sin feeds simulados: la operación en vivo solo refleja órdenes reales.
        return startSession(cmd, false);
    }

    // ── validación de combinación ─────────────────────────────────────────────

    private void validateCombination(StartSimulationCommand cmd) {
        // MANUAL no está implementado
        if (cmd.dataSource() == SimulationConfig.DataSource.MANUAL)
            throw new UnsupportedOperationException(
                    "dataSource MANUAL no implementado aún. Disponible: DB");

        // Solo una sesión MANUAL activa (futuro)
        // if (cmd.dataSource() == MANUAL && registry.hasManualSession())
        //     throw new IllegalStateException("Ya existe una sesión MANUAL activa");

        if (cmd.solverTimingMode() != SimulationConfig.SolverTimingMode.REAL_TIME)
            throw new UnsupportedOperationException(
                    "solverTimingMode '" + cmd.solverTimingMode() + "' no implementado. Disponible: REAL_TIME");

        if (cmd.optimizerMode() != SimulationConfig.OptimizerMode.ALNS_ONLY)
            throw new UnsupportedOperationException(
                    "optimizerMode '" + cmd.optimizerMode() + "' no implementado. Disponible: ALNS_ONLY");
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
        // Guardar el resultado ANTES de interrumpir/remover: usa la sesión ya en
        // mano, así que no depende de que siga en el registry.
        finishedSessionCache.record(new FinishedSessionView(
                session.getId(), session.getUsername(), SimulationSession.SimStatus.STOPPED.name(),
                Instant.now(), null, query.buildAllRoutes(session)));
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

        // CANCELLATION se resuelve por scheduleId (sin fecha): el backend decide si
        // apunta a la instancia de hoy o de mañana según la regla de 1h simulada
        // antes de la partida. No requiere que el FlightEdge ya exista en el grafo:
        // SpaceTimeGraph.cancelFlight encola la cancelación si cae fuera del horizonte.
        if (cmd.kind() == DisruptionCommand.Kind.CANCELLATION) {
            String resolvedFlightId = resolveAndCancelBySchedule(cmd.scheduleId(), runner, clock);
            System.out.printf("[SIM] Disrupción CANCELLATION en sesión %s → %s%n", sessionId, resolvedFlightId);
            return List.of(resolvedFlightId);
        }

        // AVERIA, SEGMENT_BLOCK, NODE_BLOCK — sin cambios: apuntan a instancias ya
        // expandidas en el grafo (flightId con fecha, o ventana origen/destino).
        // Solo lectura del grafo (se acepta la inconsistencia de microsegundos); la
        // mutación ocurre en el hilo del runner vía runner.submit(FlightCancelledEvent).
        List<FlightEdge> targets = new ArrayList<>();
        for (FlightEdge fe : graph.getAllFlightEdges()) {
            if (fe.isCancelled()) continue;
            boolean match = switch (cmd.kind()) {
                case CANCELLATION -> false; // ya manejado arriba
                case AVERIA ->
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

    // Resuelve a qué instancia concreta (hoy o mañana) apunta la cancelación de un
    // scheduleId, según la regla: si faltan más de 1h simulada para la partida de
    // hoy, se cancela la de hoy; si ya se pasó ese corte, se cancela la de mañana.
    // Encola en SpaceTimeGraph.cancelFlight si la instancia aún no está expandida.
    private String resolveAndCancelBySchedule(String scheduleId, SimulationRunner runner, SimulationClock clock) {
        FlightScheduleDataDTO schedule = flights.stream()
                .filter(f -> f.getId().equals(scheduleId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Vuelo (schedule) no encontrado: " + scheduleId));

        AirportDataDTO origin  = schedule.getOriginAirport();
        Instant        simNow  = clock.now();
        Instant        todayUtc = simNow.truncatedTo(java.time.temporal.ChronoUnit.DAYS);

        Instant depToday = com.tasf.b2b.domain.util.TimeUtils.combineDateAndLocalTime(
                todayUtc, schedule.getDepartureTimeLocal(), origin.getGmtOffset());
        Instant cutoff = depToday.minus(1, java.time.temporal.ChronoUnit.HOURS);

        Instant target = !simNow.isAfter(cutoff)
                ? depToday
                : com.tasf.b2b.domain.util.TimeUtils.combineDateAndLocalTime(
                        todayUtc.plus(1, java.time.temporal.ChronoUnit.DAYS),
                        schedule.getDepartureTimeLocal(), origin.getGmtOffset());

        runner.submit(new FlightCancelledEvent(clock.now(), scheduleId, target, clock));

        // Mismo formato que FlightEdge.idFlightEdge: scheduleId + fecha LOCAL (origen) de la partida.
        String datePart = com.tasf.b2b.domain.util.TimeUtils.utcToLocal(target, origin.getGmtOffset())
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return scheduleId + "-" + datePart;
    }

    private static boolean inWindow(Instant t, Instant from, Instant to) {
        if (from != null && t.isBefore(from)) return false;
        if (to != null && t.isAfter(to)) return false;
        return true;
    }

    // ── injectShipment (carga manual de órdenes) ───────────────────────────────

    /**
     * Inyecta un envío manual en una sesión en curso (típicamente la "Operación Día a
     * Día"). Construye un {@link ShipmentDataDTO} con la hora actual de la sesión como
     * entrada y lo encola vía {@link NewShipmentEvent} de forma thread-safe. El evento
     * se dispara de inmediato (su simTime = ahora) y el hilo ALNS, que sondea cada
     * ~200 ms, enruta las maletas a vuelos con capacidad disponible.
     */
    @Override
    public InjectShipmentResult injectShipment(String sessionId, InjectShipmentCommand cmd) {
        SimulationSession session = registry.findOrThrow(sessionId);

        // Cada usuario es el operador de su ciudad: si el username del operario coincide
        // con el nombre de una ciudad de la red, ESA es la sede de origen (se impone,
        // ignorando lo que envíe el cliente). Si no (p. ej. el usuario admin), se usa el
        // originIcao solicitado.
        AirportDataDTO origin = findAirportByCity(cmd.operatorUsername());
        if (origin == null && cmd.originIcao() != null) {
            origin = airports.get(cmd.originIcao());
        }
        AirportDataDTO dest = airports.get(cmd.destIcao());
        if (origin == null)
            throw new IllegalArgumentException(
                    "No se pudo determinar el aeropuerto de origen para el operador '"
                    + cmd.operatorUsername() + "'");
        if (dest == null)
            throw new IllegalArgumentException("Aeropuerto de destino no registrado: " + cmd.destIcao());
        if (origin.getIcao().equals(dest.getIcao()))
            throw new IllegalArgumentException("El aeropuerto de origen y el de destino no pueden ser el mismo");

        SimulationRunner runner = session.getRunner();
        SimulationClock  clock  = runner.getClock();
        Instant          now    = clock.now();

        String clientId = (cmd.clientId() == null || cmd.clientId().isBlank())
                ? "OPERARIO" : cmd.clientId().trim();
        String shipmentId = nextManualShipmentId(now);

        ShipmentDataDTO data = new ShipmentDataDTO(
                shipmentId, now, origin, dest, cmd.quantity(), clientId, deliveryTypes);

        // submit() es thread-safe (DelayQueue). El handler corre en el hilo del runner.
        runner.submit(new NewShipmentEvent(now, data, clock));

        List<String> baggageIds = new ArrayList<>(cmd.quantity());
        for (int i = 1; i <= cmd.quantity(); i++) {
            baggageIds.add(shipmentId + "-B" + i);
        }

        System.out.printf("[OPS] Orden manual inyectada: %s (%s→%s x%d) en sesión %s%n",
                shipmentId, origin.getIcao(), dest.getIcao(), cmd.quantity(), sessionId);

        return new InjectShipmentResult(
                shipmentId, baggageIds, origin.getIcao(), dest.getIcao(), cmd.quantity(), now);
    }

    /**
     * Id legible para órdenes manuales: {@code MAN-YYYYMMDD-NNNN}. El prefijo "MAN"
     * lo distingue de los ids numéricos de los envíos del archivo/BD.
     */
    private String nextManualShipmentId(Instant now) {
        return String.format("MAN-%s-%04d", MANUAL_ID_FMT.format(now), manualShipmentSeq.incrementAndGet());
    }

    /**
     * Busca el aeropuerto cuya ciudad coincide (sin distinguir mayúsculas) con el
     * nombre dado — usado para mapear el username del operario a su sede de origen.
     * Devuelve null si no hay coincidencia (p. ej. el usuario admin).
     */
    private AirportDataDTO findAirportByCity(String city) {
        if (city == null || city.isBlank()) return null;
        String target = normalizeCity(city);
        for (AirportDataDTO a : airports.values()) {
            if (a.getCity() != null && normalizeCity(a.getCity()).equals(target)) return a;
        }
        return null;
    }

    /** Normaliza para comparar ciudades: sin acentos, sin espacios extremos, minúsculas. */
    private static String normalizeCity(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toLowerCase();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<Thread> buildOptimizerThreads(SimulationRunner runner,
                                               SimulationClock clock,
                                               SimulationConfig config,
                                               String sessionId,
                                               OptimizerPublisher optimizerPublisher) {
        List<Thread> threads = new ArrayList<>();
        switch (config.optimizerMode()) {
            case ALNS_ONLY ->
                threads.add(new Thread(
                        new AlnsThread(runner, new ALNSAlgorithm(), clock, config, true, optimizerPublisher),
                        "alns-" + sessionId));
            case GENETIC_ONLY ->
                threads.add(new Thread(
                        new GeneticThread(runner, new GeneticAlgorithm(), clock, true),
                        "genetic-" + sessionId));
            case ALNS_ACTIVE_GENETIC_EVAL -> {
                // ALNS activo: sus rutas se aplican al grafo
                threads.add(new Thread(
                        new AlnsThread(runner, new ALNSAlgorithm(), clock, config, true, optimizerPublisher),
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
                        new AlnsThread(runner, new ALNSAlgorithm(), clock, config, false, optimizerPublisher),
                        "alns-eval-" + sessionId));
            }
        }
        return threads;
    }
}
