package com.tasf.b2b.domain.optimizer.alns.destroy;

import com.tasf.b2b.domain.optimizer.alns.AlnsProjection;
import com.tasf.b2b.domain.optimizer.alns.BaggageSolution;
import com.tasf.b2b.domain.optimizer.alns.BaggageState;
import com.tasf.b2b.domain.optimizer.alns.DestroyOperator;
import com.tasf.b2b.domain.optimizer.alns.FlightSnapshot;

import java.util.*;

/**
 * Encuentra el vuelo con menor capacidad residual (más cargado) y elimina
 * hasta k baggages que usen ese vuelo.
 */
public class OverloadedFlightRemoval implements DestroyOperator {

    @Override
    public void destroy(BaggageSolution solution, AlnsProjection projection, int k) {
        // Build flightId → (snapshot, baggages using it)
        Map<String, FlightSnapshot> snapshotById = new HashMap<>();
        Map<String, List<BaggageState>> usersByFlight = new HashMap<>();

        for (BaggageState b : solution.routedBaggages()) {
            List<FlightSnapshot> route = solution.routeOf(b);
            if (route == null) continue;
            for (FlightSnapshot f : route) {
                snapshotById.put(f.flightId(), f);
                usersByFlight.computeIfAbsent(f.flightId(), id -> new ArrayList<>()).add(b);
            }
        }

        if (snapshotById.isEmpty()) return;

        // Find most loaded flight: lowest remaining capacity after extra load
        String mostLoaded = null;
        int lowestResidual = Integer.MAX_VALUE;
        for (Map.Entry<String, FlightSnapshot> entry : snapshotById.entrySet()) {
            FlightSnapshot f = entry.getValue();
            int residual = f.remainingCapacity() - usersByFlight.getOrDefault(f.flightId(), List.of()).size();
            if (residual < lowestResidual) {
                lowestResidual = residual;
                mostLoaded = entry.getKey();
            }
        }

        if (mostLoaded == null) return;

        List<BaggageState> victims = usersByFlight.get(mostLoaded);
        int count = Math.min(k, victims.size());
        for (int i = 0; i < count; i++) {
            solution.removeRoute(victims.get(i));
        }
    }

    @Override
    public String name() { return "OverloadedFlightRemoval"; }
}
