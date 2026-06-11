package com.tasf.b2b.domain.simulator.event;

import com.tasf.b2b.domain.model.graph.componentsgraph.FlightEdge;
import com.tasf.b2b.domain.simulator.SimulationClock;

import java.time.Instant;

/**
 * Un vuelo aterriza en su destino.
 * El runner confirma el edge de cada maleta que viajaba en él.
 */
public class FlightArrivalEvent extends SimEvent {
    private final FlightEdge flightEdge;

    public FlightArrivalEvent(Instant simTime, FlightEdge flightEdge, SimulationClock clock) {
        super(simTime, clock);
        this.flightEdge = flightEdge;
    }

    public FlightEdge getFlightEdge() { return flightEdge; }
}
