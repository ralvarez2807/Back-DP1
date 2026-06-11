package com.tasf.b2b.domain.simulator.thread;

import com.tasf.b2b.domain.model.graph.SpaceTimeGraph;
import com.tasf.b2b.domain.optimizer.RoutingOptimizer;
import com.tasf.b2b.domain.optimizer.SolutionResult;
import com.tasf.b2b.domain.optimizer.alns.AlnsProjection;
import com.tasf.b2b.domain.optimizer.alns.AlnsProjectionBuilder;
import com.tasf.b2b.domain.simulator.SimulationClock;
import com.tasf.b2b.domain.simulator.SimulationConfig;
import com.tasf.b2b.domain.simulator.SimulationRunner;
import com.tasf.b2b.domain.simulator.event.RouteSolutionEvent;

/**
 * Hilo del optimizador ALNS.
 * Cuando hay baggages pendientes: construye un AlnsProjection (snapshot),
 * llama al algoritmo y, si isActive, envía el resultado al runner vía RouteSolutionEvent.
 * En modo evaluación (isActive=false) corre el algoritmo sin aplicar la solución,
 * útil para medir calidad comparativa frente al optimizador activo.
 * No escribe directamente en SpaceTimeGraph.
 */
public class AlnsThread implements Runnable {

    private static final long POLL_MS = 200;

    private final SimulationRunner runner;
    private final RoutingOptimizer optimizer;
    private final SimulationClock  clock;
    private final SimulationConfig config;
    private final boolean          isActive;

    public AlnsThread(SimulationRunner runner, RoutingOptimizer optimizer,
                      SimulationClock clock, SimulationConfig config, boolean isActive) {
        this.runner    = runner;
        this.optimizer = optimizer;
        this.clock     = clock;
        this.config    = config;
        this.isActive  = isActive;
    }

    @Override
    public void run() {
        while (runner.isRunning()) {
            try {
                SpaceTimeGraph graph = runner.getGraph();

                if (graph.getPendingBaggages().isEmpty()) {
                    Thread.sleep(POLL_MS);
                    continue;
                }

                AlnsProjection projection = AlnsProjectionBuilder.build(
                        graph, clock.now(),
                        config.minConnectionMinutes(),
                        config.pickupMinutes());

                SolutionResult result = optimizer.optimize(projection);

                if (!result.isEmpty() && isActive) {
                    runner.submit(new RouteSolutionEvent(
                            clock.now(), result.routes(),
                            result.unroutedCount(), result.alnsScore(),
                            clock));
                }

                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
