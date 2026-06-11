package com.tasf.b2b.domain.simulator.thread;

import com.tasf.b2b.domain.model.graph.SpaceTimeGraph;
import com.tasf.b2b.domain.model.graph.componentsgraph.FlightEdge;
import com.tasf.b2b.domain.model.graph.movable.Baggage;
import com.tasf.b2b.domain.optimizer.RoutingOptimizer;
import com.tasf.b2b.domain.optimizer.SolutionResult;
import com.tasf.b2b.domain.optimizer.genetic.GeneticProjection;
import com.tasf.b2b.domain.simulator.SimulationClock;
import com.tasf.b2b.domain.simulator.SimulationRunner;
import com.tasf.b2b.domain.simulator.event.RouteSolutionEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Hilo del Algoritmo Genético.
 * Cuando hay baggages pendientes: construye un GeneticProjection (snapshot),
 * llama al algoritmo y, si isActive, envía el resultado al runner vía RouteSolutionEvent.
 * En modo evaluación (isActive=false) corre el algoritmo sin aplicar la solución,
 * útil para medir calidad comparativa frente al optimizador activo.
 * No escribe directamente en SpaceTimeGraph.
 */
public class GeneticThread implements Runnable {

    private static final long POLL_MS = 200;

    private final SimulationRunner runner;
    private final RoutingOptimizer optimizer;
    private final SimulationClock  clock;
    private final boolean          isActive;

    public GeneticThread(SimulationRunner runner, RoutingOptimizer optimizer,
                         SimulationClock clock, boolean isActive) {
        this.runner    = runner;
        this.optimizer = optimizer;
        this.clock     = clock;
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

                GeneticProjection projection = snapshot(graph);
                SolutionResult    result     = optimizer.optimize(projection);

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

    private GeneticProjection snapshot(SpaceTimeGraph graph) {
        List<Baggage>    pending = new ArrayList<>(graph.getPendingBaggages());
        List<FlightEdge> flights = new ArrayList<>(graph.getAllFlightEdges());
        return new GeneticProjection(pending, flights, clock.now());
    }
}
