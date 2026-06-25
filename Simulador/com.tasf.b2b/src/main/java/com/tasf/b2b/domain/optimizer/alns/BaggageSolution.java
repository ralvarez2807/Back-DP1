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
     * (x/2) * (1 - min(ratio_i)) donde x=2, score ∈ [0, 2].
     * ratio_i = (deadline - arrTime) / (deadline - availableFrom)
     * Sin ruta → arrTime = horizonMax (peor caso).
     * Score menor = mejor. Protege al paquete más justo (min sobre todos).
     */
    public double score(Instant horizonMax) {
        double minRatio = Double.MAX_VALUE;

        for (Map.Entry<BaggageState, List<FlightSnapshot>> e : routes.entrySet()) {
            BaggageState bs = e.getKey();
            List<FlightSnapshot> route = e.getValue();
            Instant arrTime = route.isEmpty() ? horizonMax : route.getLast().arrTime();
            double ratio = ratio(bs, arrTime);
            if (ratio < minRatio) minRatio = ratio;
        }

        for (BaggageState bs : unrouted) {
            double ratio = ratio(bs, horizonMax);
            if (ratio < minRatio) minRatio = ratio;
        }

        if (minRatio == Double.MAX_VALUE) return 0.0;
        return 1.0 - minRatio;
    }

    private double ratio(BaggageState bs, Instant arrTime) {
        long window = Duration.between(bs.availableFrom(), bs.deadline()).toSeconds();
        if (window <= 0) return 0.0;
        long margin = Duration.between(arrTime, bs.deadline()).toSeconds();
        return (double) margin / window;
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
    public SolutionResult toSolutionResult(AlnsProjection projection, Instant horizonMax) {
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
        return new SolutionResult(result, partial, projection.snapshotTime(), unrouted.size(), score(horizonMax));
    }

    // ── Accessors for operators ───────────────────────────────────────────────

    public Set<BaggageState> routedBaggages()      { return routes.keySet(); }
    public List<BaggageState> unrouted()            { return unrouted; }
    public List<FlightSnapshot> routeOf(BaggageState b) { return routes.get(b); }
    public boolean hasRoute(BaggageState b)         { return routes.containsKey(b); }
    public boolean isUnrouted(BaggageState b)       { return unrouted.contains(b); }
}
