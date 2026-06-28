package com.tasf.b2b.domain.optimizer.alns.destroy;

import com.tasf.b2b.domain.optimizer.alns.AlnsProjection;
import com.tasf.b2b.domain.optimizer.alns.BaggageSolution;
import com.tasf.b2b.domain.optimizer.alns.BaggageState;
import com.tasf.b2b.domain.optimizer.alns.DestroyOperator;
import com.tasf.b2b.domain.optimizer.alns.FlightSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Elimina maletas rutadas que compiten por los mismos aeropuertos que las maletas sin ruta.
 * Objetivo: liberar capacidad de vuelo para que el operador de reparación pueda resolver
 * conflictos entre maletas sin ruta y sus competidoras, asignando en conjunto.
 *
 * Si no hay maletas sin ruta, cae a eliminación aleatoria (el ALNS no se llama para nada).
 */
public class UnroutedRelatedRemoval implements DestroyOperator {

    private final Random random;

    public UnroutedRelatedRemoval(Random random) { this.random = random; }

    @Override
    public void destroy(BaggageSolution solution, AlnsProjection projection, int k) {
        if (solution.unrouted().isEmpty()) {
            // Sin maletas sin ruta: eliminación aleatoria estándar
            List<BaggageState> routed = new ArrayList<>(solution.routedBaggages());
            Collections.shuffle(routed, random);
            int count = Math.min(k, routed.size());
            for (int i = 0; i < count; i++) solution.removeRoute(routed.get(i));
            return;
        }

        // Aeropuertos relevantes: origen y destino de todas las maletas sin ruta
        Set<String> relevantIcaos = new HashSet<>();
        for (BaggageState bs : solution.unrouted()) {
            relevantIcaos.add(bs.currentIcao());
            relevantIcaos.add(bs.destIcao());
        }

        // Puntuar maletas rutadas por relevancia (cuántos de sus vuelos tocan esos aeropuertos)
        List<BaggageState> routed = new ArrayList<>(solution.routedBaggages());
        routed.sort((a, b) -> Integer.compare(
                relevance(b, solution, relevantIcaos),
                relevance(a, solution, relevantIcaos)
        ));

        // Eliminar las k más relevantes (las que más compiten con las maletas sin ruta)
        int count = Math.min(k, routed.size());
        for (int i = 0; i < count; i++) {
            solution.removeRoute(routed.get(i));
        }
    }

    private int relevance(BaggageState bs, BaggageSolution solution, Set<String> relevantIcaos) {
        int score = 0;
        if (relevantIcaos.contains(bs.currentIcao())) score += 2;
        if (relevantIcaos.contains(bs.destIcao()))    score += 2;
        List<FlightSnapshot> route = solution.routeOf(bs);
        if (route != null) {
            for (FlightSnapshot f : route) {
                if (relevantIcaos.contains(f.fromIcao()) || relevantIcaos.contains(f.toIcao())) {
                    score += 1;
                    break;
                }
            }
        }
        return score;
    }

    @Override
    public String name() { return "UnroutedRelatedRemoval"; }
}
