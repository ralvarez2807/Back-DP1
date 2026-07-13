package com.tasf.b2b.domain.simulator.thread;

import com.tasf.b2b.domain.model.graph.SpaceTimeGraph;
import com.tasf.b2b.domain.optimizer.RoutingOptimizer;
import com.tasf.b2b.domain.optimizer.SolutionResult;
import com.tasf.b2b.domain.optimizer.alns.AlnsProjection;
import com.tasf.b2b.domain.optimizer.alns.AlnsProjectionBuilder;
import com.tasf.b2b.domain.optimizer.alns.BaggageState;
import com.tasf.b2b.domain.optimizer.metrics.AlgorithmRunDTO;
import com.tasf.b2b.domain.optimizer.metrics.CollapseDetailDTO;
import com.tasf.b2b.domain.optimizer.metrics.OptimizerPublisher;
import com.tasf.b2b.domain.simulator.CollapseDetector;
import com.tasf.b2b.domain.simulator.SimulationClock;
import com.tasf.b2b.domain.simulator.SimulationConfig;
import com.tasf.b2b.domain.simulator.SimulationRunner;
import com.tasf.b2b.domain.simulator.event.CollapseDetectedEvent;
import com.tasf.b2b.domain.simulator.event.RouteSolutionEvent;

import java.time.Duration;
import java.util.List;

/**
 * Hilo del optimizador ALNS.
 * Cuando hay baggages pendientes: construye un AlnsProjection (snapshot),
 * llama al algoritmo y, si isActive, envía el resultado al runner vía RouteSolutionEvent.
 * Publica métricas de cada ejecución (tiempo, score, promedio) en el canal optimizer WebSocket.
 * En modo evaluación (isActive=false) corre el algoritmo sin aplicar la solución,
 * útil para medir calidad comparativa frente al optimizador activo.
 * No escribe directamente en SpaceTimeGraph.
 */
public class AlnsThread implements Runnable {

    // Poll base a speedFactor=1; se achica proporcionalmente en sesiones más rápidas.
    private static final long BASE_POLL_MS = 200;
    private static final long MIN_POLL_MS  = 10;

    private final SimulationRunner  runner;
    private final RoutingOptimizer  optimizer;
    private final SimulationClock   clock;
    private final SimulationConfig  config;
    private final boolean           isActive;
    private final OptimizerPublisher optimizerPublisher;
    private final CollapseDetector  collapseDetector = new CollapseDetector();
    private       boolean           collapseSubmitted = false;

    // Estadísticas acumuladas de ejecuciones
    private int    runCount   = 0;
    private double totalScore = 0.0;

    public AlnsThread(SimulationRunner runner, RoutingOptimizer optimizer,
                      SimulationClock clock, SimulationConfig config, boolean isActive,
                      OptimizerPublisher optimizerPublisher) {
        this.runner             = runner;
        this.optimizer          = optimizer;
        this.clock              = clock;
        this.config             = config;
        this.isActive           = isActive;
        this.optimizerPublisher = optimizerPublisher;
    }

    @Override
    public void run() {
        while (runner.isRunning()) {
            try {
                SpaceTimeGraph graph = runner.getGraph();

                if (graph.getPendingBaggages().isEmpty()) {
                    Thread.sleep(pollMs());
                    continue;
                }

                AlnsProjection projection = AlnsProjectionBuilder.build(
                        graph, clock.now(),
                        config.minConnectionMinutes(),
                        config.pickupMinutes());

                // Medir tiempo de ejecución del optimizador
                long startNs = System.nanoTime();
                SolutionResult result = optimizer.optimize(projection);
                long executionMs = (System.nanoTime() - startNs) / 1_000_000;

                // Actualizar estadísticas y publicar métricas de esta ejecución
                runCount++;
                totalScore += result.alnsScore();
                double avgScore = totalScore / runCount;

                optimizerPublisher.publish(new AlgorithmRunDTO(
                        clock.now(),
                        runCount,
                        executionMs,
                        result.routes().size(),
                        result.unroutedCount(),
                        result.alnsScore(),
                        avgScore));

                // Detección de colapso (solo si está habilitada en la config)
                if (config.collapseOnFailure() && !collapseSubmitted) {
                    collapseDetector.check(projection, result).ifPresent(info -> {
                        publishCollapseDetail(info, projection.snapshotTime());
                        runner.submit(new CollapseDetectedEvent(clock.now(), info, clock));
                        collapseSubmitted = true;
                    });
                }

                if (!result.isEmpty() && isActive) {
                    runner.submit(new RouteSolutionEvent(
                            clock.now(), result.routes(),
                            result.unroutedCount(), result.alnsScore(),
                            clock));
                }

                // Si ya hay más trabajo pendiente (llegó durante el solve), reoptimiza
                // de inmediato; si no, duerme antes de volver a chequear.
                if (graph.getPendingBaggages().isEmpty()) {
                    Thread.sleep(pollMs());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Poll proporcional a speedFactor: sesiones más rápidas chequean más seguido. */
    private long pollMs() {
        long scaled = (long) (BASE_POLL_MS / config.speedFactor());
        return Math.max(MIN_POLL_MS, scaled);
    }

    private void publishCollapseDetail(CollapseDetector.CollapseInfo info, java.time.Instant snapshotTime) {
        List<CollapseDetailDTO.UnroutedBaggageInfo> details = info.unroutedBaggages().stream()
                .map(bs -> new CollapseDetailDTO.UnroutedBaggageInfo(
                        bs.baggageId(),
                        bs.currentIcao(),
                        bs.destIcao(),
                        bs.availableFrom(),
                        bs.deadline(),
                        Math.max(0, Duration.between(bs.deadline(), snapshotTime).toMinutes())))
                .toList();

        optimizerPublisher.publish(new CollapseDetailDTO(
                clock.now(),
                info.reason(),
                info.consecutiveCycles(),
                details));
    }
}
