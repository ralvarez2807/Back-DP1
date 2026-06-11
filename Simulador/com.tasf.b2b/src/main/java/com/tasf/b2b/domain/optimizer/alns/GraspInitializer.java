package com.tasf.b2b.domain.optimizer.alns;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Construye una solución inicial greedy ordenando baggages por deadline (más urgente primero).
 * Cada baggage recibe la primera ruta viable encontrada por RouteFinder.
 */
class GraspInitializer {

    static BaggageSolution initialize(AlnsProjection projection, Random random) {
        List<BaggageState> sorted = new ArrayList<>(projection.pendingBaggages());
        sorted.sort(Comparator.comparing(BaggageState::deadline));

        BaggageSolution solution = BaggageSolution.empty(sorted);

        for (BaggageState baggage : sorted) {
            List<FlightSnapshot> route = RouteFinder.findRoute(baggage, solution, projection);
            if (!route.isEmpty()) {
                solution.addRoute(baggage, route);
            }
        }
        return solution;
    }
}
