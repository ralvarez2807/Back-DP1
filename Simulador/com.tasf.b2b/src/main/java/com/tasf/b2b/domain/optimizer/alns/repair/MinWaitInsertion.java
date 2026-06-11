package com.tasf.b2b.domain.optimizer.alns.repair;

import com.tasf.b2b.domain.optimizer.alns.AlnsProjection;
import com.tasf.b2b.domain.optimizer.alns.BaggageSolution;
import com.tasf.b2b.domain.optimizer.alns.BaggageState;
import com.tasf.b2b.domain.optimizer.alns.FlightSnapshot;
import com.tasf.b2b.domain.optimizer.alns.RepairOperator;
import com.tasf.b2b.domain.optimizer.alns.RouteFinder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Inserta baggages sin ruta minimizando el número de escalas (menor cantidad de vuelos).
 * Útil como variante complementaria a GreedyInsertion.
 */
public class MinWaitInsertion implements RepairOperator {

    @Override
    public void repair(BaggageSolution solution, AlnsProjection projection) {
        List<BaggageState> unrouted = new ArrayList<>(solution.unrouted());
        unrouted.sort(Comparator.comparing(BaggageState::deadline));

        for (BaggageState baggage : unrouted) {
            List<FlightSnapshot> route = RouteFinder.findRouteMinHops(baggage, solution, projection);
            if (!route.isEmpty()) {
                solution.addRoute(baggage, route);
            }
        }
    }

    @Override
    public String name() { return "MinWaitInsertion"; }
}
