package com.tasf.b2b.domain.optimizer.alns.destroy;

import com.tasf.b2b.domain.optimizer.alns.AlnsProjection;
import com.tasf.b2b.domain.optimizer.alns.BaggageSolution;
import com.tasf.b2b.domain.optimizer.alns.BaggageState;
import com.tasf.b2b.domain.optimizer.alns.DestroyOperator;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Elige aleatoriamente una ventana de 2 horas alrededor de un deadline
 * y elimina hasta k baggages cuyos deadlines caigan en esa ventana.
 */
public class TimeWindowRemoval implements DestroyOperator {

    private static final Duration HALF_WINDOW = Duration.ofHours(1);

    private final Random random;

    public TimeWindowRemoval(Random random) { this.random = random; }

    @Override
    public void destroy(BaggageSolution solution, AlnsProjection projection, int k) {
        List<BaggageState> routed = new ArrayList<>(solution.routedBaggages());
        if (routed.isEmpty()) return;

        BaggageState pivot = routed.get(random.nextInt(routed.size()));
        Instant center = pivot.deadline();
        Instant windowStart = center.minus(HALF_WINDOW);
        Instant windowEnd   = center.plus(HALF_WINDOW);

        int count = 0;
        for (BaggageState b : routed) {
            if (count >= k) break;
            Instant dl = b.deadline();
            if (!dl.isBefore(windowStart) && !dl.isAfter(windowEnd)) {
                solution.removeRoute(b);
                count++;
            }
        }
    }

    @Override
    public String name() { return "TimeWindowRemoval"; }
}
