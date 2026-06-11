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
            Instant availableFrom = b.getCurrentEdge() != null
                    ? max(b.getCurrentEdge().getFromNode().getTimeUtc(), snapshotTime)
                    : snapshotTime;
            pending.add(new BaggageState(
                    b.getId(),
                    b.getCurrentAirport(),
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
