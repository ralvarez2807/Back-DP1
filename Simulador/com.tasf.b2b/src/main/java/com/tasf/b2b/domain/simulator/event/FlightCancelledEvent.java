package com.tasf.b2b.domain.simulator.event;

import com.tasf.b2b.domain.simulator.SimulationClock;

import java.time.Instant;

/**
 * Cancelación de un vuelo concreto.
 * Inyectable desde el hilo externo de cancelaciones.
 * Firma compatible con SpaceTimeGraph.cancelFlight(scheduleKey, depTimeUtc).
 */
public class FlightCancelledEvent extends SimEvent {
    private final String  flightScheduleKey;
    private final Instant depTimeUtc;

    public FlightCancelledEvent(Instant simTime, String flightScheduleKey,
                                Instant depTimeUtc, SimulationClock clock) {
        super(simTime, clock);
        this.flightScheduleKey = flightScheduleKey;
        this.depTimeUtc        = depTimeUtc;
    }

    public String  getFlightScheduleKey() { return flightScheduleKey; }
    public Instant getDepTimeUtc()        { return depTimeUtc; }
}
