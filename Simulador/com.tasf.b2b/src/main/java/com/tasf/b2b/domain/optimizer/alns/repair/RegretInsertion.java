package com.tasf.b2b.domain.optimizer.alns.repair;

import com.tasf.b2b.domain.optimizer.alns.AlnsProjection;
import com.tasf.b2b.domain.optimizer.alns.BaggageSolution;
import com.tasf.b2b.domain.optimizer.alns.BaggageState;
import com.tasf.b2b.domain.optimizer.alns.FlightSnapshot;
import com.tasf.b2b.domain.optimizer.alns.RepairOperator;
import com.tasf.b2b.domain.optimizer.alns.RouteFinder;

import java.time.Instant;
import java.util.*;

/**
 * Inserta primero el baggage con mayor regret: diferencia entre la mejor y la segunda-mejor ruta.
 * Baggages sin segunda ruta tienen regret infinito y se insertan primero.
 */
public class RegretInsertion implements RepairOperator {

    @Override
    public void repair(BaggageSolution solution, AlnsProjection projection) {
        List<BaggageState> unrouted = new ArrayList<>(solution.unrouted());

        while (!unrouted.isEmpty()) {
            BaggageState best = null;
            List<FlightSnapshot> bestRoute = null;
            double bestRegret = Double.NEGATIVE_INFINITY;

            for (BaggageState baggage : unrouted) {
                List<FlightSnapshot> route1 = RouteFinder.findRoute(baggage, solution, projection);
                if (route1.isEmpty()) continue;

                Set<String> blacklist = flightIds(route1);
                List<FlightSnapshot> route2 = RouteFinder.findRoute(baggage, solution, projection, blacklist);

                double cost1 = arrivalCost(route1, baggage);
                double cost2 = route2.isEmpty() ? Double.MAX_VALUE : arrivalCost(route2, baggage);
                double regret = cost2 - cost1;

                if (regret > bestRegret) {
                    bestRegret = regret;
                    best = baggage;
                    bestRoute = route1;
                }
            }

            // If any unrouted baggage has no route at all, skip it this iteration
            if (best == null) break;

            solution.addRoute(best, bestRoute);
            unrouted.remove(best);
        }
    }

    private double arrivalCost(List<FlightSnapshot> route, BaggageState baggage) {
        if (route.isEmpty()) return Double.MAX_VALUE;
        Instant arr      = route.getLast().arrTime();
        Instant deadline = baggage.deadline();
        long lateSeconds = Math.max(0, arr.getEpochSecond() - deadline.getEpochSecond());
        return arr.getEpochSecond() + lateSeconds * 1000L;
    }

    private Set<String> flightIds(List<FlightSnapshot> route) {
        Set<String> ids = new HashSet<>();
        for (FlightSnapshot f : route) ids.add(f.flightId());
        return ids;
    }

    @Override
    public String name() { return "RegretInsertion"; }
}
