package com.tasf.b2b.domain.simulator.event;

import com.tasf.b2b.domain.model.graph.componentsgraph.FlightEdge;
import com.tasf.b2b.domain.simulator.SimulationClock;

import java.time.Instant;

/** Un vuelo sale de su aeropuerto de origen. */
public class FlightDepartureEvent extends SimEvent {
    private final FlightEdge flightEdge;

    public FlightDepartureEvent(Instant simTime, FlightEdge flightEdge, SimulationClock clock) {
        super(simTime, clock);
        this.flightEdge = flightEdge;
    }

    public FlightEdge getFlightEdge() { return flightEdge; }
}
