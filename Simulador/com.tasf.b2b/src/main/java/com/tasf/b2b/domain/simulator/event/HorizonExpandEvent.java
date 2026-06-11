package com.tasf.b2b.domain.simulator.event;

import com.tasf.b2b.domain.simulator.SimulationClock;

import java.time.Instant;

/** Dispara la expansión diaria del grafo espacio-temporal. Se auto-reprograma. */
public class HorizonExpandEvent extends SimEvent {
    public HorizonExpandEvent(Instant simTime, SimulationClock clock) {
        super(simTime, clock);
    }
}
