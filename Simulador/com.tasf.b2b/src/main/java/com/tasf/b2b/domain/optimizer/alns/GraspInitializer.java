package com.tasf.b2b.domain.optimizer.alns;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Construye una solución inicial con dos arranques: greedy por deadline y orden aleatorio.
 * Devuelve el arranque con menos maletas sin ruta, dando al ALNS un punto de partida mejor
 * sin coste significativo (dos pasadas lineales sobre los baggages).
 */
class GraspInitializer {

    static BaggageSolution initialize(AlnsProjection projection, Random random) {
        List<BaggageState> byDeadline = new ArrayList<>(projection.pendingBaggages());
        byDeadline.sort(Comparator.comparing(BaggageState::deadline));
        BaggageSolution greedy = buildFrom(byDeadline, projection);

        List<BaggageState> shuffled = new ArrayList<>(projection.pendingBaggages());
        Collections.shuffle(shuffled, random);
        BaggageSolution randomized = buildFrom(shuffled, projection);

        return randomized.unrouted().size() < greedy.unrouted().size() ? randomized : greedy;
    }

    private static BaggageSolution buildFrom(List<BaggageState> order, AlnsProjection projection) {
        BaggageSolution sol = BaggageSolution.empty(order);
        for (BaggageState baggage : order) {
            List<FlightSnapshot> route = RouteFinder.findRoute(baggage, sol, projection);
            if (!route.isEmpty()) sol.addRoute(baggage, route);
        }
        return sol;
    }
}
