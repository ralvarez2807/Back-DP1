package com.tasf.b2b.domain.simulator.event;

import com.tasf.b2b.domain.simulator.SimulationClock;

import java.time.Instant;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * Evento base de la simulación.
 * Implementa Delayed para que DelayQueue lo despache en el momento real correcto:
 *   wallDeadline = wallStart + (simTime − simStart) / speedFactor
 */
public abstract class SimEvent implements Delayed {
    private final Instant          simTime;
    private final long             wallDeadlineMs;
    private final SimulationClock  clock;

    protected SimEvent(Instant simTime, SimulationClock clock) {
        this.simTime        = simTime;
        this.clock          = clock;
        this.wallDeadlineMs = clock.toWallDeadlineMs(simTime);
    }

    public Instant getSimTime() { return simTime; }

    /**
     * Usa effectiveWallTimeMs() en lugar de currentTimeMillis() para que
     * la pausa congele el avance de todos los eventos sin vaciar la cola.
     */
    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(wallDeadlineMs - clock.effectiveWallTimeMs(), TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed other) {
        return Long.compare(
                this.getDelay(TimeUnit.NANOSECONDS),
                other.getDelay(TimeUnit.NANOSECONDS));
    }
}
