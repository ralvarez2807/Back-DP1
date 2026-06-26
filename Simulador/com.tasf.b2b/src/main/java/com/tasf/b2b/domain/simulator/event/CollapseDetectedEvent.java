package com.tasf.b2b.domain.simulator.event;

import com.tasf.b2b.domain.simulator.CollapseDetector;
import com.tasf.b2b.domain.simulator.SimulationClock;

import java.time.Instant;

/** Señal de colapso del planificador. Detiene la simulación. */
public class CollapseDetectedEvent extends SimEvent {

    private final CollapseDetector.CollapseInfo info;

    public CollapseDetectedEvent(Instant simTime, CollapseDetector.CollapseInfo info, SimulationClock clock) {
        super(simTime, clock);
        this.info = info;
    }

    public CollapseDetector.CollapseInfo getInfo() { return info; }
}
