package com.tasf.b2b.domain.optimizer.alns;

import com.tasf.b2b.domain.model.graph.SpaceTimeGraph;
import com.tasf.b2b.domain.model.graph.componentsgraph.FlightEdge;
import com.tasf.b2b.domain.model.graph.componentsgraph.STEdge;
import com.tasf.b2b.domain.model.graph.movable.Baggage;

import java.time.Instant;
import java.util.*;

public class AlnsProjectionBuilder {

    /**
     * Instante a partir del cual la maleta puede embarcar su siguiente vuelo.
     *
     * <p>Una maleta recién registrada está lista de inmediato: el envío entra al almacén y
     * puede tomar cualquier vuelo que aún no haya salido, sin margen previo. El margen de
     * {@code minConnectionMinutes} es exclusivo de las <b>escalas</b> — aterrizar y volver a
     * embarcar exige el procesamiento en almacén — y por eso se aplica solo cuando la maleta
     * ya voló al menos un tramo (o está volando ahora mismo).</p>
     *
     * <p>El margen de recojo en destino ({@code pickupMinutes}) no interviene aquí: ese se
     * descuenta del deadline del último tramo, en {@code RouteFinder}.</p>
     */
    public static Instant availableFrom(Baggage b, Instant snapshotTime, int minConnectionMinutes) {
        long connectSec = minConnectionMinutes * 60L;

        // En vuelo: aterrizará y hará escala → el margen corre desde su llegada.
        if (b.getCurrentEdge() instanceof FlightEdge fe) {
            return max(fe.getToNode().getTimeUtc().plusSeconds(connectSec), snapshotTime);
        }

        // En tierra habiendo volado ya: está en escala → margen desde el último aterrizaje.
        List<STEdge> traveled = b.getRouteTraveled();
        if (!traveled.isEmpty()) {
            STEdge last = traveled.get(traveled.size() - 1);
            return max(last.getToNode().getTimeUtc().plusSeconds(connectSec), snapshotTime);
        }

        // En su aeropuerto de origen, sin tramos recorridos: lista ya.
        return snapshotTime;
    }

    public static AlnsProjection build(SpaceTimeGraph graph, Instant snapshotTime,
                                       int minConnectionMinutes, int pickupMinutes) {
        Collection<FlightEdge> liveFlights = graph.getAllFlightEdges();

        Map<String, Baggage>    baggageById = new HashMap<>();
        Map<String, FlightEdge> flightById  = new HashMap<>();
        List<BaggageState>      pending     = new ArrayList<>();

        for (Baggage b : new ArrayList<>(graph.getPendingBaggages())) {
            baggageById.put(b.getId(), b);
            String  currentIcao;
            if (b.getCurrentEdge() instanceof FlightEdge fe) {
                // Maleta en vuelo: llegará al nodo destino, no está en el origen
                currentIcao = fe.getToNode().getIcao();
            } else {
                // Maleta esperando en aeropuerto (WaitEdge o sin arista)
                currentIcao = b.getCurrentAirport();
            }
            Instant availableFrom = availableFrom(b, snapshotTime, minConnectionMinutes);
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
