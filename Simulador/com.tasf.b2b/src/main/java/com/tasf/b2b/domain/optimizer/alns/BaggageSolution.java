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
     * Objetivo lexicográfico en un solo double. Score menor = mejor.
     *
     * Primario:   unrouted.size() × 2.0   (cada maleta sin ruta penaliza +2)
     * Secundario: promedio de (1-ratio_i) sobre maletas rutadas  ∈ [0, 1]
     *
     * Como el máximo costo de una maleta rutada es 1.0 (llega exactamente al deadline —
     * RouteFinder descarta llegadas tardías), la penalidad 2.0 garantiza que CUALQUIER
     * solución con K sin ruta sea peor que CUALQUIER solución con K-1 sin ruta,
     * sin importar cuán buenas sean el resto:
     *   K × 2.0  >  (K-1) × 2.0 + 1.0   ⟺   0 > -1  ✓
     *
     * ratio_i = (deadline - arrTime) / (deadline - availableFrom)
     */
    public double score(Instant horizonMax) {
        if (routes.isEmpty() && unrouted.isEmpty()) return 0.0;
        double routedCost = 0.0;
        for (Map.Entry<BaggageState, List<FlightSnapshot>> e : routes.entrySet()) {
            BaggageState bs = e.getKey();
            List<FlightSnapshot> route = e.getValue();
            Instant arrTime = route.isEmpty() ? horizonMax : route.getLast().arrTime();
            routedCost += (1.0 - ratio(bs, arrTime));
        }
        double routedAvg = routes.isEmpty() ? 0.0 : routedCost / routes.size();
        return unrouted.size() * 2.0 + routedAvg;
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
