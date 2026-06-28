package com.tasf.b2b.domain.optimizer.alns;

import com.tasf.b2b.domain.model.graph.SpaceTimeGraph;
import com.tasf.b2b.domain.model.graph.componentsgraph.FlightEdge;
import com.tasf.b2b.domain.model.graph.movable.Baggage;

import java.time.Instant;
import java.util.*;

public class AlnsProjectionBuilder {

    public static AlnsProjection build(SpaceTimeGraph graph, Instant snapshotTime,
                                       int minConnectionMinutes, int pickupMinutes) {
        Collection<FlightEdge> liveFlights = graph.getAllFlightEdges();

        Map<String, Baggage>    baggageById = new HashMap<>();
        Map<String, FlightEdge> flightById  = new HashMap<>();
        List<BaggageState>      pending     = new ArrayList<>();

        for (Baggage b : new ArrayList<>(graph.getPendingBaggages())) {
            baggageById.put(b.getId(), b);
            String  currentIcao;
            Instant availableFrom;
            if (b.getCurrentEdge() instanceof FlightEdge fe) {
                // Maleta en vuelo: llegará al nodo destino, no está en el origen
                currentIcao   = fe.getToNode().getIcao();
                availableFrom = max(fe.getToNode().getTimeUtc(), snapshotTime);
            } else {
                // Maleta esperando en aeropuerto (WaitEdge o sin arista)
                currentIcao   = b.getCurrentAirport();
                availableFrom = snapshotTime;
            }
            pending.add(new BaggageState(
                    b.getId(),
                    currentIcao,
                    availableFrom,
                    b.getDestIcao(),
                    b.getDeadlineUtc()
            ));
        }

        Map<String, NavigableMap<Instant, List<FlightSnapshot>>> flightsByOrigin = new HashMap<>();
        for (FlightEdge fe : liveFlights) {
            if (fe.isCancelled()) continue;
            flightById.put(fe.getIdFlightEdge(), fe);
            FlightSnapshot snap = new FlightSnapshot(
                    fe.getIdFlightEdge(),
                    fe.getFromNode().getIcao(),
                    fe.getToNode().getIcao(),
                    fe.getFromNode().getTimeUtc(),
                    fe.getToNode().getTimeUtc(),
                    fe.getRemainingCapacity()
            );
            flightsByOrigin
                    .computeIfAbsent(snap.fromIcao(), k -> new TreeMap<>())
                    .computeIfAbsent(snap.depTime(), k -> new ArrayList<>())
                    .add(snap);
        }

        return new AlnsProjection(
                pending,
                flightsByOrigin,
                minConnectionMinutes,
                pickupMinutes,
                snapshotTime,
                baggageById,
                flightById
        );
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
