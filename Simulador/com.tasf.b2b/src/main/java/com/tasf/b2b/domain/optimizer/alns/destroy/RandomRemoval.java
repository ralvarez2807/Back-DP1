package com.tasf.b2b.domain.optimizer.alns.destroy;

import com.tasf.b2b.domain.optimizer.alns.AlnsProjection;
import com.tasf.b2b.domain.optimizer.alns.BaggageSolution;
import com.tasf.b2b.domain.optimizer.alns.BaggageState;
import com.tasf.b2b.domain.optimizer.alns.DestroyOperator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class RandomRemoval implements DestroyOperator {

    private final Random random;

    public RandomRemoval(Random random) { this.random = random; }

    @Override
    public void destroy(BaggageSolution solution, AlnsProjection projection, int k) {
        List<BaggageState> routed = new ArrayList<>(solution.routedBaggages());
        Collections.shuffle(routed, random);
        int count = Math.min(k, routed.size());
        for (int i = 0; i < count; i++) {
            solution.removeRoute(routed.get(i));
        }
    }

    @Override
    public String name() { return "RandomRemoval"; }
}
