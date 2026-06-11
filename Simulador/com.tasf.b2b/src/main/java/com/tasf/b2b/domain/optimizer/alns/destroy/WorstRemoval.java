package com.tasf.b2b.domain.optimizer.alns.destroy;

import com.tasf.b2b.domain.optimizer.alns.AlnsProjection;
import com.tasf.b2b.domain.optimizer.alns.BaggageSolution;
import com.tasf.b2b.domain.optimizer.alns.BaggageState;
import com.tasf.b2b.domain.optimizer.alns.DestroyOperator;
import com.tasf.b2b.domain.optimizer.alns.FlightSnapshot;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Elimina los k baggages con mayor retraso respecto a su deadline.
 */
public class WorstRemoval implements DestroyOperator {

    @Override
    public void destroy(BaggageSolution solution, AlnsProjection projection, int k) {
        List<BaggageState> routed = new ArrayList<>(solution.routedBaggages());

        routed.sort(Comparator.comparingLong((BaggageState b) -> {
            List<FlightSnapshot> route = solution.routeOf(b);
            if (route == null || route.isEmpty()) return 0L;
            return Duration.between(b.deadline(), route.getLast().arrTime()).toSeconds();
        }).reversed());

        int count = Math.min(k, routed.size());
        for (int i = 0; i < count; i++) {
            solution.removeRoute(routed.get(i));
        }
    }

    @Override
    public String name() { return "WorstRemoval"; }
}
