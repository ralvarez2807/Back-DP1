package com.tasf.b2b.domain.optimizer.alns;

import com.tasf.b2b.domain.model.graph.projection.GraphProjection;
import com.tasf.b2b.domain.optimizer.RoutingOptimizer;
import com.tasf.b2b.domain.optimizer.SolutionResult;
import com.tasf.b2b.domain.optimizer.alns.acceptance.SimulatedAnnealing;
import com.tasf.b2b.domain.optimizer.alns.destroy.OverloadedFlightRemoval;
import com.tasf.b2b.domain.optimizer.alns.destroy.RandomRemoval;
import com.tasf.b2b.domain.optimizer.alns.destroy.ShawRemoval;
import com.tasf.b2b.domain.optimizer.alns.destroy.TimeWindowRemoval;
import com.tasf.b2b.domain.optimizer.alns.destroy.WorstRemoval;
import com.tasf.b2b.domain.optimizer.alns.repair.GreedyInsertion;
import com.tasf.b2b.domain.optimizer.alns.repair.MinWaitInsertion;
import com.tasf.b2b.domain.optimizer.alns.repair.RegretInsertion;

import java.util.List;
import java.util.Random;

public class ALNSAlgorithm implements RoutingOptimizer {

    private static final long   TIME_BUDGET_MS   = 150;
    private static final double REWARD_NEW_BEST   = 3.0;
    private static final double REWARD_ACCEPTED   = 1.0;
    private static final double REWARD_REJECTED   = 0.0;
    private static final double INITIAL_TEMP      = 50.0;
    private static final double COOLING_RATE      = 0.97;

    private final Random random;

    public ALNSAlgorithm(Random random) {
        this.random = random;
    }

    public ALNSAlgorithm() {
        this(new Random());
    }

    @Override
    public SolutionResult optimize(GraphProjection graphProjection) {
        AlnsProjection projection = (AlnsProjection) graphProjection;

        if (projection.pendingBaggages().isEmpty()) {
            return SolutionResult.empty();
        }

        List<DestroyOperator> destroyOps = List.of(
                new RandomRemoval(random),
                new ShawRemoval(random),
                new WorstRemoval(),
                new TimeWindowRemoval(random),
                new OverloadedFlightRemoval()
        );
        List<RepairOperator> repairOps = List.of(
                new GreedyInsertion(),
                new RegretInsertion(),
                new MinWaitInsertion()
        );

        RouletteWheelSelector<DestroyOperator> destroySel = new RouletteWheelSelector<>(destroyOps, random);
        RouletteWheelSelector<RepairOperator>  repairSel  = new RouletteWheelSelector<>(repairOps, random);
        AcceptanceCriterion acceptance = new SimulatedAnnealing(INITIAL_TEMP, COOLING_RATE, random);

        BaggageSolution current = GraspInitializer.initialize(projection, random);
        BaggageSolution best    = current.deepCopy();

        int k = Math.max(1, projection.pendingBaggages().size() / 5);

        long deadline = System.currentTimeMillis() + TIME_BUDGET_MS;

        while (System.currentTimeMillis() < deadline) {
            BaggageSolution candidate = current.deepCopy();

            DestroyOperator destroy = destroySel.select();
            RepairOperator  repair  = repairSel.select();

            destroy.destroy(candidate, projection, k);
            repair.repair(candidate, projection);

            double candidateScore = candidate.score();
            double currentScore   = current.score();
            double bestScore      = best.score();

            if (candidateScore < bestScore) {
                best    = candidate.deepCopy();
                current = candidate;
                destroySel.reward(REWARD_NEW_BEST);
                repairSel.reward(REWARD_NEW_BEST);
            } else if (acceptance.accept(candidateScore, currentScore)) {
                current = candidate;
                destroySel.reward(REWARD_ACCEPTED);
                repairSel.reward(REWARD_ACCEPTED);
            } else {
                destroySel.reward(REWARD_REJECTED);
                repairSel.reward(REWARD_REJECTED);
            }
        }

        return best.toSolutionResult(projection);
    }
}
