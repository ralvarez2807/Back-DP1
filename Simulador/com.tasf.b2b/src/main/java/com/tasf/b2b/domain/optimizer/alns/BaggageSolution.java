package com.tasf.b2b.domain.optimizer.alns;

import com.tasf.b2b.domain.model.graph.componentsgraph.FlightEdge;
import com.tasf.b2b.domain.model.graph.componentsgraph.STEdge;
import com.tasf.b2b.domain.model.graph.movable.Baggage;
import com.tasf.b2b.domain.optimizer.SolutionResult;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Solución mutable que el ALNS construye y modifica.
 * Trabaja exclusivamente con BaggageState y FlightSnapshot — nunca con objetos del grafo vivo.
 */
public class BaggageSolution {

    private final Map<BaggageState, List<FlightSnapshot>> routes;
    private final List<BaggageState>                      unrouted;
    private final Map<String, Integer>                    flightExtraLoad;

    private BaggageSolution(Map<BaggageState, List<FlightSnapshot>> routes,
                            List<BaggageState> unrouted,
                            Map<String, Integer> flightExtraLoad) {
        this.routes          = routes;
        this.unrouted        = unrouted;
        this.flightExtraLoad = flightExtraLoad;
    }

    public static BaggageSolution empty(List<BaggageState> pending) {
        return new BaggageSolution(new LinkedHashMap<>(), new ArrayList<>(pending), new HashMap<>());
    }

    // ── Mutators ──────────────────────────────────────────────────────────────

    public void addRoute(BaggageState baggage, List<FlightSnapshot> route) {
        unrouted.remove(baggage);
        routes.put(baggage, new ArrayList<>(route));
        for (FlightSnapshot f : route) {
            flightExtraLoad.merge(f.flightId(), 1, Integer::sum);
        }
    }

    public void removeRoute(BaggageState baggage) {
        List<FlightSnapshot> route = routes.remove(baggage);
        if (route != null) {
            for (FlightSnapshot f : route) {
                flightExtraLoad.merge(f.flightId(), -1, Integer::sum);
            }
        }
        if (!unrouted.contains(baggage)) {
            unrouted.add(baggage);
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public boolean flightHasCapacity(FlightSnapshot flight) {
        int extra = flightExtraLoad.getOrDefault(flight.flightId(), 0);
        return (flight.remainingCapacity() - extra) > 0;
    }

    /**
     * Score multi-objetivo con tres niveles de prioridad:
     *   1. 1000 pt por maleta sin ruta  (dominante)
     *   2. horas de retraso sobre el deadline  (secundario)
     *   3. TRANSIT_WEIGHT × horas de tránsito total  (tiebreaker)
     *
     * El nivel 3 garantiza que entre dos soluciones igualmente a tiempo,
     * el ALNS prefiere la que entrega antes. El peso es lo bastante pequeño
     * para que 24 h de tránsito extra (0.024 pts) nunca justifique aceptar
     * 90 segundos de retraso (0.025 pts).
     */
    private static final double TRANSIT_WEIGHT = 0.001;

    public double score() {
        double penalty = unrouted.size() * 1000.0;
        for (Map.Entry<BaggageState, List<FlightSnapshot>> e : routes.entrySet()) {
            BaggageState         baggage = e.getKey();
            List<FlightSnapshot> route   = e.getValue();
            if (route.isEmpty()) continue;

            Instant arrTime  = route.getLast().arrTime();
            Instant deadline = baggage.deadline();

            // Nivel 2: penalidad por llegada tardía
            long lateSeconds = Duration.between(deadline, arrTime).toSeconds();
            if (lateSeconds > 0) {
                penalty += lateSeconds / 3600.0;
            }

            // Nivel 3: minimizar tránsito total (desde que está disponible hasta que llega)
            long transitSeconds = Duration.between(baggage.availableFrom(), arrTime).toSeconds();
            if (transitSeconds > 0) {
                penalty += TRANSIT_WEIGHT * transitSeconds / 3600.0;
            }
        }
        return penalty;
    }

    public BaggageSolution deepCopy() {
        Map<BaggageState, List<FlightSnapshot>> routesCopy = new LinkedHashMap<>();
        for (Map.Entry<BaggageState, List<FlightSnapshot>> e : routes.entrySet()) {
            routesCopy.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return new BaggageSolution(routesCopy, new ArrayList<>(unrouted), new HashMap<>(flightExtraLoad));
    }

    /**
     * Convierte al resultado que el runner espera.
     * Usa las referencias vivas de la proyección solo en este paso final.
     */
    public SolutionResult toSolutionResult(AlnsProjection projection) {
        Map<Baggage, List<STEdge>> result = new LinkedHashMap<>();
        for (Map.Entry<BaggageState, List<FlightSnapshot>> e : routes.entrySet()) {
            Baggage liveBaggage = projection.baggageById().get(e.getKey().baggageId());
            if (liveBaggage == null) continue;
            List<STEdge> liveEdges = new ArrayList<>();
            for (FlightSnapshot fs : e.getValue()) {
                FlightEdge fe = projection.flightById().get(fs.flightId());
                if (fe != null) liveEdges.add(fe);
            }
            if (!liveEdges.isEmpty()) {
                result.put(liveBaggage, liveEdges);
            }
        }
        boolean partial = !unrouted.isEmpty();
        return new SolutionResult(result, partial, projection.snapshotTime(), unrouted.size(), score());
    }

    // ── Accessors for operators ───────────────────────────────────────────────

    public Set<BaggageState> routedBaggages()      { return routes.keySet(); }
    public List<BaggageState> unrouted()            { return unrouted; }
    public List<FlightSnapshot> routeOf(BaggageState b) { return routes.get(b); }
    public boolean hasRoute(BaggageState b)         { return routes.containsKey(b); }
    public boolean isUnrouted(BaggageState b)       { return unrouted.contains(b); }
}
