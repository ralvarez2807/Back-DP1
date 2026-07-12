package com.tasf.b2b.domain.optimizer.alns;

import com.tasf.b2b.domain.model.graph.projection.GraphProjection;
import com.tasf.b2b.domain.optimizer.RoutingOptimizer;
import com.tasf.b2b.domain.optimizer.SolutionResult;
import com.tasf.b2b.domain.optimizer.alns.acceptance.SimulatedAnnealing;
import com.tasf.b2b.domain.optimizer.alns.destroy.OverloadedFlightRemoval;
import com.tasf.b2b.domain.optimizer.alns.destroy.RandomRemoval;
import com.tasf.b2b.domain.optimizer.alns.destroy.ShawRemoval;
import com.tasf.b2b.domain.optimizer.alns.destroy.TimeWindowRemoval;
import com.tasf.b2b.domain.optimizer.alns.destroy.UnroutedRelatedRemoval;
import com.tasf.b2b.domain.optimizer.alns.destroy.WorstRemoval;
import com.tasf.b2b.domain.optimizer.alns.repair.GreedyInsertion;
import com.tasf.b2b.domain.optimizer.alns.repair.MinWaitInsertion;
import com.tasf.b2b.domain.optimizer.alns.repair.RegretInsertion;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class ALNSAlgorithm implements RoutingOptimizer {

    private static final long   TIME_BUDGET_MS  = 175;
    // Máximo de maletas optimizadas por ciclo. Acota el costo de cada optimize():
    // sin esto, con un backlog grande GRASP hacía 2×N búsquedas de ruta (Dijkstra, sin
    // límite de tiempo) y cada optimize() tardaba segundos; sus soluciones llegaban
    // obsoletas —el primer vuelo ya había partido, ver SimulationRunner.handleRouteSolution—
    // y se descartaban → el backlog crecía sin fin. Se procesan las MÁS URGENTES (por
    // deadline) primero; el resto se atiende en los ciclos siguientes (el hilo ALNS
    // corre en bucle continuo mientras haya pendientes).
    private static final int    MAX_BAGGAGES_PER_CYCLE = 200;
    private static final double REWARD_NEW_BEST = 3.0;
    private static final double REWARD_ACCEPTED = 1.0;
    private static final double REWARD_REJECTED = 0.0;
    // Temperatura inicial bajada de 50 a 20: con ~10 iteraciones por ejecución,
    // 50 casi nunca enfría (0.97^10 = 0.74 × 50 = 37°). Con 20 y 0.90, llegamos
    // a 20 × 0.90^10 = 7° al final, lo que da selectividad real al SA.
    private static final double INITIAL_TEMP    = 20.0;
    private static final double COOLING_RATE    = 0.90;

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

        // Conjunto de trabajo acotado: las maletas MÁS URGENTES (por deadline). Mantiene
        // el costo por ciclo constante aunque el backlog sea enorme, para que la solución
        // se aplique fresca. Si hay pocas pendientes se toman todas (sin cambio de comportamiento).
        List<BaggageState> workingSet = projection.pendingBaggages();
        if (workingSet.size() > MAX_BAGGAGES_PER_CYCLE) {
            workingSet = workingSet.stream()
                    .sorted(Comparator.comparing(BaggageState::deadline))
                    .limit(MAX_BAGGAGES_PER_CYCLE)
                    .toList();
        }

        List<DestroyOperator> destroyOps = List.of(
                new RandomRemoval(random),
                new ShawRemoval(random),
                new WorstRemoval(),
                new TimeWindowRemoval(random),
                new OverloadedFlightRemoval(),
                new UnroutedRelatedRemoval(random)
        );
        List<RepairOperator> repairOps = List.of(
                new GreedyInsertion(),
                new RegretInsertion(),
                new MinWaitInsertion()
        );

        RouletteWheelSelector<DestroyOperator> destroySel = new RouletteWheelSelector<>(destroyOps, random);
        RouletteWheelSelector<RepairOperator>  repairSel  = new RouletteWheelSelector<>(repairOps, random);
        AcceptanceCriterion acceptance = new SimulatedAnnealing(INITIAL_TEMP, COOLING_RATE, random);

        Instant horizonMax = projection.flightsByOrigin().values().stream()
                .flatMap(m -> m.values().stream())
                .flatMap(Collection::stream)
                .map(FlightSnapshot::arrTime)
                .max(Comparator.naturalOrder())
                .orElseGet(() -> projection.snapshotTime().plusSeconds(14L * 24 * 3600));

        BaggageSolution current = GraspInitializer.initialize(projection, workingSet, random);
        BaggageSolution best    = current.deepCopy();

        int baseK = Math.max(1, workingSet.size() / 5);
        int maxK  = Math.max(1, workingSet.size() / 3);

        long deadline = System.currentTimeMillis() + TIME_BUDGET_MS;

        // Scores cacheados: antes se recomputaban current y best (O(rutadas)) en CADA
        // iteración aunque no cambiaran. Ahora solo se calcula el del candidato y se
        // actualizan current/best cuando efectivamente cambian.
        double currentScore = current.score(horizonMax);
        double bestScore    = currentScore;

        while (System.currentTimeMillis() < deadline) {
            BaggageSolution candidate = current.deepCopy();

            // k crece cuando hay maletas sin ruta: destrucción más agresiva para
            // intentar resolver conflictos de capacidad.
            int k = Math.min(maxK, Math.max(baseK, current.unrouted().size()));

            DestroyOperator destroy = destroySel.select();
            RepairOperator  repair  = repairSel.select();

            destroy.destroy(candidate, projection, k);
            repair.repair(candidate, projection);

            double candidateScore = candidate.score(horizonMax);

            if (candidateScore < bestScore) {
                best         = candidate.deepCopy();
                bestScore    = candidateScore;
                current      = candidate;
                currentScore = candidateScore;
                destroySel.reward(REWARD_NEW_BEST);
                repairSel.reward(REWARD_NEW_BEST);
            } else if (acceptance.accept(candidateScore, currentScore)) {
                current      = candidate;
                currentScore = candidateScore;
                destroySel.reward(REWARD_ACCEPTED);
                repairSel.reward(REWARD_ACCEPTED);
            } else {
                destroySel.reward(REWARD_REJECTED);
                repairSel.reward(REWARD_REJECTED);
            }
        }

        return best.toSolutionResult(projection, horizonMax);
    }
}
