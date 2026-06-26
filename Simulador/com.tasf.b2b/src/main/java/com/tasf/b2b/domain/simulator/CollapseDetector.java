package com.tasf.b2b.domain.simulator;

import com.tasf.b2b.domain.model.graph.movable.Baggage;
import com.tasf.b2b.domain.optimizer.SolutionResult;
import com.tasf.b2b.domain.optimizer.alns.AlnsProjection;
import com.tasf.b2b.domain.optimizer.alns.BaggageState;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Detecta el colapso del planificador en dos condiciones:
 *
 * DEADLINE_EXCEEDED: uno o más baggages pendientes superaron su deadline sin ser asignados.
 *
 * NO_VIABLE_ROUTE: el mismo conjunto de baggages queda sin ruta durante
 *   CYCLE_THRESHOLD ciclos consecutivos — no existe ningún camino posible en el grafo.
 */
public class CollapseDetector {

    private static final int CYCLE_THRESHOLD = 5;

    private Set<String> lastUnroutedIds  = Set.of();
    private int         consecutiveCycles = 0;

    public enum CollapseReason { DEADLINE_EXCEEDED, NO_VIABLE_ROUTE }

    public record CollapseInfo(
            CollapseReason     reason,
            List<BaggageState> unroutedBaggages,
            int                consecutiveCycles
    ) {
        public String  primaryBaggageId() { return unroutedBaggages.isEmpty() ? "?" : unroutedBaggages.get(0).baggageId(); }
        public Instant primaryDeadline()  { return unroutedBaggages.isEmpty() ? Instant.now() : unroutedBaggages.get(0).deadline(); }
    }

    public Optional<CollapseInfo> check(AlnsProjection projection, SolutionResult result) {
        // Condición A: uno o más baggages pendientes superaron su deadline
        List<BaggageState> overdue = projection.pendingBaggages().stream()
                .filter(bs -> bs.deadline().isBefore(projection.snapshotTime()))
                .toList();
        if (!overdue.isEmpty()) {
            return Optional.of(new CollapseInfo(CollapseReason.DEADLINE_EXCEEDED, overdue, 0));
        }

        // Condición B: mismo conjunto de baggages sin ruta durante N ciclos consecutivos
        if (result.unroutedCount() > 0) {
            Set<String> routedIds = result.routes().keySet().stream()
                    .map(Baggage::getId)
                    .collect(Collectors.toSet());

            Set<String> unroutedIds = projection.pendingBaggages().stream()
                    .map(BaggageState::baggageId)
                    .filter(id -> !routedIds.contains(id))
                    .collect(Collectors.toSet());

            if (unroutedIds.equals(lastUnroutedIds)) {
                consecutiveCycles++;
            } else {
                consecutiveCycles = 1;
                lastUnroutedIds   = unroutedIds;
            }

            if (consecutiveCycles >= CYCLE_THRESHOLD) {
                List<BaggageState> stuckBaggages = projection.pendingBaggages().stream()
                        .filter(bs -> lastUnroutedIds.contains(bs.baggageId()))
                        .toList();
                return Optional.of(new CollapseInfo(CollapseReason.NO_VIABLE_ROUTE, stuckBaggages, consecutiveCycles));
            }
        } else {
            consecutiveCycles = 0;
            lastUnroutedIds   = Set.of();
        }

        return Optional.empty();
    }
}
