package com.tasf.b2b.domain.optimizer.alns;

import java.time.Instant;
import java.util.*;

/**
 * Dijkstra sobre el snapshot del grafo; minimiza el tiempo de llegada al destino.
 * Nunca accede a objetos vivos del SpaceTimeGraph.
 */
public class RouteFinder {

    private static final int MAX_HOPS = 8;

    /**
     * @param blacklistedFlights flightIds a ignorar (para RegretInsertion)
     * @return ruta de FlightSnapshots o vacío si no existe ruta viable
     */
    public static List<FlightSnapshot> findRoute(BaggageState baggage,
                                                 BaggageSolution solution,
                                                 AlnsProjection projection,
                                                 Set<String> blacklistedFlights) {
        int pickupMin  = projection.pickupMinutes();
        int connectMin = projection.minConnectionMinutes();

        // Estado: (arrivalTime, icao, path, hops)
        // Prioridad: menor arrivalTime primero
        PriorityQueue<State> pq = new PriorityQueue<>(Comparator.comparing(s -> s.time));
        Map<String, Instant> bestArrival = new HashMap<>();

        Instant startTime = baggage.availableFrom().plusSeconds(pickupMin * 60L);
        pq.offer(new State(startTime, baggage.currentIcao(), List.of(), 0));

        while (!pq.isEmpty()) {
            State current = pq.poll();

            if (current.hops >= MAX_HOPS) continue;

            NavigableMap<Instant, List<FlightSnapshot>> byTime =
                    projection.flightsByOrigin().get(current.icao);
            if (byTime == null) continue;

            for (Map.Entry<Instant, List<FlightSnapshot>> entry :
                    byTime.tailMap(current.time).entrySet()) {
                for (FlightSnapshot flight : entry.getValue()) {
                    if (blacklistedFlights.contains(flight.flightId())) continue;
                    if (!solution.flightHasCapacity(flight))             continue;
                    if (flight.arrTime().isAfter(baggage.deadline()))    continue;

                    List<FlightSnapshot> newPath = append(current.path, flight);

                    if (flight.toIcao().equals(baggage.destIcao())) {
                        return newPath;
                    }

                    Instant nextAvail = flight.arrTime().plusSeconds(connectMin * 60L);
                    Instant prev      = bestArrival.get(flight.toIcao());
                    if (prev != null && !nextAvail.isBefore(prev)) continue;

                    bestArrival.put(flight.toIcao(), nextAvail);
                    pq.offer(new State(nextAvail, flight.toIcao(), newPath, current.hops + 1));
                }
            }
        }
        return List.of();
    }

    public static List<FlightSnapshot> findRoute(BaggageState baggage,
                                                 BaggageSolution solution,
                                                 AlnsProjection projection) {
        return findRoute(baggage, solution, projection, Set.of());
    }

    /**
     * BFS minimizando número de escalas. Usado por MinWaitInsertion.
     */
    public static List<FlightSnapshot> findRouteMinHops(BaggageState baggage,
                                                        BaggageSolution solution,
                                                        AlnsProjection projection) {
        int pickupMin  = projection.pickupMinutes();
        int connectMin = projection.minConnectionMinutes();

        // PQ keyed on hop count, then arrival time
        PriorityQueue<State> pq = new PriorityQueue<>(
                Comparator.comparingInt((State s) -> s.hops).thenComparing(s -> s.time));
        Map<String, Integer> bestHops = new HashMap<>();

        Instant startTime = baggage.availableFrom().plusSeconds(pickupMin * 60L);
        pq.offer(new State(startTime, baggage.currentIcao(), List.of(), 0));

        while (!pq.isEmpty()) {
            State current = pq.poll();

            if (current.hops >= MAX_HOPS) continue;

            Integer prevHops = bestHops.get(current.icao);
            if (prevHops != null && current.hops > prevHops) continue;

            NavigableMap<Instant, List<FlightSnapshot>> byTime =
                    projection.flightsByOrigin().get(current.icao);
            if (byTime == null) continue;

            for (Map.Entry<Instant, List<FlightSnapshot>> entry :
                    byTime.tailMap(current.time).entrySet()) {
                for (FlightSnapshot flight : entry.getValue()) {
                    if (!solution.flightHasCapacity(flight))          continue;
                    if (flight.arrTime().isAfter(baggage.deadline())) continue;

                    List<FlightSnapshot> newPath = append(current.path, flight);

                    if (flight.toIcao().equals(baggage.destIcao())) {
                        return newPath;
                    }

                    int nextHops      = current.hops + 1;
                    Integer prevBest  = bestHops.get(flight.toIcao());
                    if (prevBest != null && nextHops >= prevBest) continue;

                    bestHops.put(flight.toIcao(), nextHops);
                    Instant nextAvail = flight.arrTime().plusSeconds(connectMin * 60L);
                    pq.offer(new State(nextAvail, flight.toIcao(), newPath, nextHops));
                }
            }
        }
        return List.of();
    }

    private static List<FlightSnapshot> append(List<FlightSnapshot> path, FlightSnapshot f) {
        List<FlightSnapshot> next = new ArrayList<>(path.size() + 1);
        next.addAll(path);
        next.add(f);
        return next;
    }

    private record State(Instant time, String icao, List<FlightSnapshot> path, int hops) {}
}
