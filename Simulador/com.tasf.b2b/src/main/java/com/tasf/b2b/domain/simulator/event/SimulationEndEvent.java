package com.tasf.b2b.domain.simulator.event;

import com.tasf.b2b.domain.simulator.SimulationClock;

import java.time.Instant;

/** Señal de fin de simulación. Detiene el loop del runner. */
public class SimulationEndEvent extends SimEvent {
    public SimulationEndEvent(Instant simTime, SimulationClock clock) {
        super(simTime, clock);
    }
}
