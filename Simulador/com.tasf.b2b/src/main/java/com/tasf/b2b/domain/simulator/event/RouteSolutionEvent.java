package com.tasf.b2b.domain.simulator.event;

import com.tasf.b2b.domain.model.graph.componentsgraph.STEdge;
import com.tasf.b2b.domain.model.graph.movable.Baggage;
import com.tasf.b2b.domain.simulator.SimulationClock;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Solución calculada por el hilo ALNS.
 * Lleva una ruta (solo FlightEdges) por cada maleta pendiente que resolvió.
 * El runner aplica los cambios en su propio hilo para evitar condiciones de carrera.
 */
public class RouteSolutionEvent extends SimEvent {
    private final Map<Baggage, List<STEdge>> routes;
    private final int    unroutedCount;
    private final double alnsScore;

    public RouteSolutionEvent(Instant simTime,
                              Map<Baggage, List<STEdge>> routes,
                              int unroutedCount,
                              double alnsScore,
                              SimulationClock clock) {
        super(simTime, clock);
        this.routes       = Map.copyOf(routes);
        this.unroutedCount = unroutedCount;
        this.alnsScore     = alnsScore;
    }

    public Map<Baggage, List<STEdge>> getRoutes()    { return routes; }
    public int    getUnroutedCount()                 { return unroutedCount; }
    public double getAlnsScore()                     { return alnsScore; }
}
