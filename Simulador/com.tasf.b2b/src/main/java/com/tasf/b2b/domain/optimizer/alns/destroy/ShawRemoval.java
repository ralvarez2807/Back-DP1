package com.tasf.b2b.domain.optimizer.alns.destroy;

import com.tasf.b2b.domain.optimizer.alns.AlnsProjection;
import com.tasf.b2b.domain.optimizer.alns.BaggageSolution;
import com.tasf.b2b.domain.optimizer.alns.BaggageState;
import com.tasf.b2b.domain.optimizer.alns.DestroyOperator;
import com.tasf.b2b.domain.optimizer.alns.FlightSnapshot;

import java.time.Duration;
import java.util.*;

/**
 * Elimina baggages similares entre sí para facilitar su re-inserción coordinada.
 * Similitud: mismo origen (+1), mismo destino (+1), deadlines cercanos (+1), vuelo compartido (+1).
 */
public class ShawRemoval implements DestroyOperator {

    private static final Duration DEADLINE_WINDOW = Duration.ofHours(4);

    private final Random random;

    public ShawRemoval(Random random) { this.random = random; }

    @Override
    public void destroy(BaggageSolution solution, AlnsProjection projection, int k) {
        List<BaggageState> routed = new ArrayList<>(solution.routedBaggages());
        if (routed.isEmpty()) return;

        BaggageState seed = routed.get(random.nextInt(routed.size()));
        solution.removeRoute(seed);

        Set<String> seedFlights = flightIds(solution, seed);
        List<BaggageState> remaining = new ArrayList<>(solution.routedBaggages());

        remaining.sort((a, b) -> Double.compare(similarity(b, seed, solution, seedFlights),
                                                 similarity(a, seed, solution, seedFlights)));

        int count = Math.min(k - 1, remaining.size());
        for (int i = 0; i < count; i++) {
            solution.removeRoute(remaining.get(i));
        }
    }

    private double similarity(BaggageState b, BaggageState seed,
                               BaggageSolution solution, Set<String> seedFlights) {
        double score = 0;
        if (b.currentIcao().equals(seed.currentIcao())) score += 1;
        if (b.destIcao().equals(seed.destIcao()))       score += 1;

        long deadlineDiffH = Math.abs(Duration.between(b.deadline(), seed.deadline()).toHours());
        if (deadlineDiffH <= DEADLINE_WINDOW.toHours()) score += 1;

        List<FlightSnapshot> bRoute = solution.routeOf(b);
        if (bRoute != null) {
            for (FlightSnapshot f : bRoute) {
                if (seedFlights.contains(f.flightId())) { score += 1; break; }
            }
        }
        return score;
    }

    private Set<String> flightIds(BaggageSolution solution, BaggageState b) {
        List<FlightSnapshot> route = solution.routeOf(b);
        if (route == null) return Set.of();
        Set<String> ids = new HashSet<>();
        for (FlightSnapshot f : route) ids.add(f.flightId());
        return ids;
    }

    @Override
    public String name() { return "ShawRemoval"; }
}
