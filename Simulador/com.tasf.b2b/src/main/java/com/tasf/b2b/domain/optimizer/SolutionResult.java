package com.tasf.b2b.domain.optimizer;

import com.tasf.b2b.domain.model.graph.componentsgraph.STEdge;
import com.tasf.b2b.domain.model.graph.movable.Baggage;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Resultado inmutable de una ejecución del optimizador. Nunca null.
 * routes: solo FlightEdges — las WaitEdges las gestiona el runner.
 * isPartial: true si el optimizador no pudo asignar todos los baggages pendientes.
 */
public record SolutionResult(
        Map<Baggage, List<STEdge>> routes,
        boolean isPartial,
        Instant generatedAt,
        int unroutedCount,
        double alnsScore
) {
    public boolean isEmpty() { return routes.isEmpty(); }

    public static SolutionResult empty() {
        return new SolutionResult(Map.of(), false, Instant.now(), 0, 0.0);
    }
}
